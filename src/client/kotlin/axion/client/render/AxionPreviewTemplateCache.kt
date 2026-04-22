package axion.client.render

import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BuiltBuffer
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.util.BufferAllocator
import net.minecraft.util.math.BlockPos
import java.util.LinkedHashMap

/**
 * Template-based GPU buffer cache for preview rendering.
 *
 * Instead of tessellating every (clipboard × origin) combination, we tessellate
 * the clipboard ONCE at its offset positions with camera at (0,0,0), producing
 * a template GPU buffer. Then we draw this template at each origin by adjusting
 * the model-view matrix offset.
 *
 * This collapses the cache from O(clipboard × origins) to O(clipboard),
 * and reduces tessellation work from N origins to 1.
 *
 * Cache key: (clipboard, color, alpha, scale) — no origin dependency.
 */
object AxionPreviewTemplateCache {
    private const val MAX_CACHE_SIZE = 32

    private data class TemplateKey(
        val clipboard: ClipboardBuffer,
        val color: Int,
        val alpha: Int,
        val scale: Float,
    )

    data class TemplateEntry(
        val cachedVertexData: ByteArray,
        val cachedDrawParams: BuiltBuffer.DrawParameters,
        val renderLayer: RenderLayer,
        val isReady: Boolean,
        val scale: Float,
    )

    private val cache = object : LinkedHashMap<TemplateKey, TemplateEntry>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TemplateKey, TemplateEntry>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    /**
     * Get or create a template GPU buffer for the given clipboard.
     * The template is tessellated at offset positions with camera at (0,0,0).
     *
     * Returns null if clipboard has no renderable blocks.
     * Returns an entry with isReady=false if the buffer is still being uploaded
     * (caller should fall back to Phase 2/1 for one frame).
     */
    private val LOGGER = org.slf4j.LoggerFactory.getLogger("AxionTemplateCache")

    fun getOrUpload(
        clipboard: ClipboardBuffer,
        color: Int,
        alpha: Int,
        scale: Float,
        maxBlocks: Int = 1536,
    ): TemplateEntry? = synchronized(cache) {
        val key = TemplateKey(clipboard, color, alpha, scale)
        val existing = cache[key]
        if (existing != null) return@synchronized existing

        val layer = RenderLayerCompat.blockTranslucentCull()
        val builtBuffer = tessellateTemplate(clipboard, layer, color, alpha, maxBlocks)
        if (builtBuffer == null) {
            LOGGER.debug("[Axion] tessellateTemplate returned null for {} cells", clipboard.nonAirCells().size)
            return@synchronized null
        }

        // Cache raw vertex bytes so we can reconstruct BuiltBuffer each frame
        val vertexBuffer = builtBuffer.buffer
        val vertexBytes = ByteArray(vertexBuffer.remaining())
        vertexBuffer.get(vertexBytes)
        val drawParams = builtBuffer.drawParameters
        LOGGER.debug("[Axion] tessellated: vertexBytes={}, vertexCount={}, indexCount={}", vertexBytes.size, drawParams.vertexCount(), drawParams.indexCount())
        builtBuffer.close()

        TemplateEntry(
            cachedVertexData = vertexBytes,
            cachedDrawParams = drawParams,
            renderLayer = layer,
            isReady = true,
            scale = scale,
        ).also { cache[key] = it }
    }

    /**
     * Draw the cached template at each origin using MC's own RenderLayer.draw().
     * Translates the model-view matrix per origin, reconstructs a BuiltBuffer from
     * cached vertex bytes, and delegates all pipeline/texture/uniform setup to MC.
     *
     * Each origin gets its own BufferAllocator which is properly closed after draw
     * to avoid native memory leaks.
     */
    fun drawAtOrigins(
        entry: TemplateEntry,
        origins: Collection<BlockPos>,
    ) {
        if (entry.cachedVertexData.isEmpty() || origins.isEmpty()) return

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val modelViewStack = RenderSystem.getModelViewStack()
        val scale = entry.scale

        for (origin in origins) {
            modelViewStack.pushMatrix()
            modelViewStack.translate(
                (origin.x - cameraPos.x).toFloat(),
                (origin.y - cameraPos.y).toFloat(),
                (origin.z - cameraPos.z).toFloat(),
            )
            if (scale != 1.0f) {
                modelViewStack.translate(0.5f, 0.5f, 0.5f)
                modelViewStack.scale(scale, scale, scale)
                modelViewStack.translate(-0.5f, -0.5f, -0.5f)
            }

            // Allocator must be closed after draw to prevent native memory leak.
            // RenderLayer.draw() closes the BuiltBuffer (and inner CloseableBuffer),
            // but the BufferAllocator that owns the native memory must be closed separately.
            val allocator = BufferAllocator(entry.cachedVertexData.size)
            try {
                val builtBuffer = reconstructBuiltBuffer(entry, allocator)
                if (builtBuffer != null) {
                    entry.renderLayer.draw(builtBuffer)
                }
            } finally {
                allocator.close()
            }

            modelViewStack.popMatrix()
        }
    }

    /**
     * Reconstruct a fresh BuiltBuffer from cached vertex bytes.
     * MC's RenderLayer.draw() closes the buffer after drawing, so we must
     * create a new one each frame. The cost is just a memcpy — much cheaper
     * than re-tessellation.
     *
     * The caller owns the [allocator] and must close it after the BuiltBuffer
     * has been consumed (drawn or discarded).
     */
    private fun reconstructBuiltBuffer(entry: TemplateEntry, allocator: BufferAllocator): BuiltBuffer? {
        val data = entry.cachedVertexData
        if (data.isEmpty()) return null

        allocator.allocate(data.size)
        val closeableBuffer = allocator.getAllocated() ?: return null
        val buffer = closeableBuffer.buffer
        buffer.put(data)
        buffer.rewind()
        return BuiltBuffer(closeableBuffer, entry.cachedDrawParams)
    }

    fun invalidate() {
        synchronized(cache) {
            cache.clear()
        }
    }

    fun invalidateForClipboard(clipboard: ClipboardBuffer) {
        synchronized(cache) {
            val keysToRemove = cache.keys.filter { it.clipboard == clipboard }
            keysToRemove.forEach { key -> cache.remove(key) }
        }
    }

    /**
     * Tessellate the clipboard as a template: blocks at their offset positions
     * with camera at (0,0,0). Vertex positions will be relative to the clipboard
     * origin, allowing translation via model-view matrix at draw time.
     */
    private fun tessellateTemplate(
        clipboard: ClipboardBuffer,
        layer: RenderLayer,
        color: Int,
        alpha: Int,
        maxBlocks: Int,
    ): BuiltBuffer? {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return null
        val alphaScale = alpha / 255.0f

        val surfaceCells = filterSurfaceCells(clipboard.nonAirCells())
        val cellsToRender = if (surfaceCells.size <= maxBlocks) surfaceCells else downsampleCells(surfaceCells, maxBlocks)
        if (cellsToRender.isEmpty()) return null

        // Build PreviewBlockInfo at offset positions and statesByPosition for AO
        val blocks = ArrayList<PreviewBlockInfo>(cellsToRender.size)
        val statesByPosition = LinkedHashMap<Long, net.minecraft.block.BlockState>(cellsToRender.size)
        cellsToRender.forEach { cell ->
            val offsetPos = BlockPos(cell.offset.x, cell.offset.y, cell.offset.z)
            if (cell.state.renderType == net.minecraft.block.BlockRenderType.MODEL) {
                blocks += PreviewBlockInfo(pos = offsetPos, state = cell.state)
            }
            statesByPosition[offsetPos.asLong()] = cell.state
        }
        if (blocks.isEmpty()) return null

        val previewView = AxionBlockTessellator.TemplateBlockRenderView(world, statesByPosition)
        val allocator = BufferAllocator(layer.expectedBufferSize)
        val bufferBuilder = BufferBuilder(allocator, layer.drawMode, layer.vertexFormat)
        // Use fullBright=true because template tessellation happens at offset positions
        // (0-based), not real world positions. The world lighting provider would return
        // incorrect (often dark) light values for these positions.
        val consumer = TintedAlphaVertexConsumer(bufferBuilder, alphaScale, color, fullBright = true)

        val tessellateStack = net.minecraft.client.util.math.MatrixStack()

        // Tessellate at offset positions with camera at (0,0,0)
        val rendered = AxionBlockTessellator.tessellateBatch(
            blocks = blocks,
            world = previewView,
            matrixStack = tessellateStack,
            consumer = consumer,
            cameraX = 0.0,
            cameraY = 0.0,
            cameraZ = 0.0,
            checkSides = true,
        )
        LOGGER.warn("[Axion] tessellateTemplate: blocks={}, rendered={}", blocks.size, rendered)

        val result = bufferBuilder.endNullable()
        if (result == null) {
            LOGGER.warn("[Axion] tessellateTemplate: endNullable returned null (no vertices produced)")
        }
        return result
    }

    private fun filterSurfaceCells(cells: List<ClipboardCell>): List<ClipboardCell> {
        val positionSet = HashSet<Long>(cells.size)
        cells.forEach { cell ->
            positionSet.add(BlockPos.asLong(cell.offset.x, cell.offset.y, cell.offset.z))
        }
        return cells.filter { cell ->
            if (cell.state.isAir) return@filter false
            net.minecraft.util.math.Direction.entries.any { face ->
                val neighborKey = BlockPos.asLong(
                    cell.offset.x + face.offsetX,
                    cell.offset.y + face.offsetY,
                    cell.offset.z + face.offsetZ,
                )
                !positionSet.contains(neighborKey)
            }
        }
    }

    private fun downsampleCells(cells: List<ClipboardCell>, maxCells: Int): List<ClipboardCell> {
        if (cells.size <= maxCells) return cells
        val result = ArrayList<ClipboardCell>(maxCells)
        val lastIndex = cells.lastIndex
        for (index in 0 until maxCells) {
            val sourceIndex = ((index.toLong() * lastIndex) / (maxCells - 1).coerceAtLeast(1)).toInt()
            result += cells[sourceIndex]
        }
        return result
    }
}

package axion.client.render.gpu
import axion.client.compat.CameraAccess

import axion.client.render.AxionBlockTessellator
import axion.client.render.AxionPreviewBuffer
import axion.client.render.AxionWorldRenderContext
import axion.client.render.PreviewBlockInfo
import axion.client.render.RenderLayerCompat
import axion.client.render.TintedAlphaVertexConsumer
import axion.client.render.getBuffer
import axion.common.model.ClipboardBuffer
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.BuiltBuffer
import net.minecraft.client.util.BufferAllocator
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.slf4j.LoggerFactory
import org.joml.Matrix4f

class ChunkedPreviewSession(val previewId: String) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(ChunkedPreviewSession::class.java)

    companion object {
        private const val DEBUG_LOG: Boolean = false
        private const val LOG_INTERVAL_MS: Long = 1000
        private const val USE_DIRECT_GPU_DRAW: Boolean = true
        private const val DEBUG_FORCE_LEGACY_PATH: Boolean = false
    }

    private var lastLogTime: Long = 0
    private fun shouldLog(): Boolean {
        if (!DEBUG_LOG) return false
        val now = System.currentTimeMillis()
        if (now - lastLogTime < LOG_INTERVAL_MS) return false
        lastLogTime = now
        return true
    }

    private val store = ChunkedBooleanStore()
    private val states = ChunkedStateMap()
    private val chunkBuffers = Long2ObjectOpenHashMap<AxionPreviewBuffer>()
    private var lastSignature: Long = 0
    private var anchoredClipboard: ClipboardBuffer? = null
    private var anchoredSingleOrigin: BlockPos? = null
    private var translationDelta: Vec3i = Vec3i.ZERO

    val isEmpty: Boolean get() = store.isEmpty()
    val totalCells: Int get() = store.size
    fun totalSurfaceCells(): Int = store.size

    fun setFromClipboard(clipboard: ClipboardBuffer, origins: Collection<BlockPos>): Boolean {
        val signature = computeSignature(clipboard, origins)
        if (signature == lastSignature && lastSignature != 0L) return false

        val nextSingleOrigin = if (origins.size == 1) origins.first() else null
        if (
            clipboard === anchoredClipboard &&
            nextSingleOrigin != null &&
            anchoredSingleOrigin != null &&
            !chunkBuffers.isEmpty()
        ) {
            val anchor = anchoredSingleOrigin ?: nextSingleOrigin
            translationDelta = Vec3i(
                nextSingleOrigin.x - anchor.x,
                nextSingleOrigin.y - anchor.y,
                nextSingleOrigin.z - anchor.z,
            )
            lastSignature = signature
            return true
        }

        lastSignature = signature
        rebuildFull(clipboard, origins)
        anchoredClipboard = clipboard
        anchoredSingleOrigin = nextSingleOrigin
        translationDelta = Vec3i.ZERO
        return true
    }

    private fun rebuildFull(clipboard: ClipboardBuffer, origins: Collection<BlockPos>) {
        val previousPositions = HashSet<Long>(store.size)
        store.forEachSection { sectionKey, _ ->
            iterateAllCellsInSection(store, sectionKey) { packed -> previousPositions += packed }
        }

        val newPositions = HashSet<Long>()
        val occupiedCells = clipboard.nonAirCells()
        origins.forEach { origin ->
            occupiedCells.forEach { cell ->
                val pos = cell.absolutePos(origin)
                val packed = pos.asLong()
                newPositions += packed
                states.put(pos, cell.state)
            }
        }

        previousPositions.forEach { packed ->
            if (packed !in newPositions) {
                store.remove(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed))
                states.remove(packed)
            }
        }
        newPositions.forEach { packed ->
            if (packed !in previousPositions) {
                store.add(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed))
            }
        }
    }

    fun clear() {
        store.clear()
        states.clear()
        chunkBuffers.values.forEach { it.close() }
        chunkBuffers.clear()
        lastSignature = 0
    }

    fun render(
        context: AxionWorldRenderContext,
        color: Int,
        alpha: Int,
    ): ChunkedDrawResult {
        if (store.isEmpty()) return ChunkedDrawResult.NO_BUFFERS

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return ChunkedDrawResult.FAILED

        val log = shouldLog()
        val dirtyCountBefore = if (log) store.dirtySnapshot().size else 0

        refreshDirtyBuffers(world)
        if (chunkBuffers.isEmpty()) {
            if (log) logger.info("[Axion diag] session={} store.size={} dirtyBefore={} chunkBuffers=0 → NO_BUFFERS", previewId, store.size, dirtyCountBefore)
            return ChunkedDrawResult.NO_BUFFERS
        }

        if (!USE_DIRECT_GPU_DRAW || DEBUG_FORCE_LEGACY_PATH || AxionPreviewBlockDrawer.isDisabled()) {
            renderLegacy(context, world, color, alpha, translationDelta)
            return ChunkedDrawResult.DREW
        }

        val camera = client.gameRenderer.camera ?: return ChunkedDrawResult.FAILED
        val cameraPos = CameraAccess.getPos(camera)
        val baseModelView = Matrix4f(context.matrices().peek().positionMatrix)
        return drawDeferred(color, alpha, translationDelta, baseModelView, cameraPos)
    }

    fun drawDeferred(
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
        baseModelView: Matrix4f,
        cameraPos: Vec3d? = null,
        cullingModelView: org.joml.Matrix4fc? = null,
        projectionMatrix: org.joml.Matrix4fc? = null,
    ): ChunkedDrawResult {
        if (chunkBuffers.isEmpty()) return ChunkedDrawResult.NO_BUFFERS
        return AxionPreviewBlockDrawer.drawChunked(
            chunkBuffers, color, alpha, translationDelta,
            baseModelView, cameraPos, cullingModelView, projectionMatrix,
        )
    }

    override fun close() { clear() }

    private fun refreshDirtyBuffers(world: ClientWorld) {
        val dirty = store.consumeDirty()
        if (dirty.isEmpty()) return

        val statesView = states.asMap()
        val iter = dirty.iterator()
        while (iter.hasNext()) {
            val sectionKey = iter.nextLong()
            val rawSection = store.rawSection(sectionKey)
            if (rawSection == null) {
                chunkBuffers.remove(sectionKey)?.close()
                continue
            }

            val builtBuffer = buildSectionBuffer(sectionKey, world, statesView)
            if (builtBuffer == null) {
                chunkBuffers.remove(sectionKey)?.close()
            } else {
                chunkBuffers.computeIfAbsent(sectionKey) { AxionPreviewBuffer() }.upload(builtBuffer)
                builtBuffer.close()
            }
        }
    }

    private fun buildSectionBuffer(
        sectionKey: Long,
        world: ClientWorld,
        statesByPosition: Map<Long, BlockState>,
    ): BuiltBuffer? {
        val surface = ChunkMeshTessellator.buildSectionSurface(store, sectionKey, statesByPosition)
        if (surface.isEmpty()) return null

        val blocks = ArrayList<PreviewBlockInfo>(surface.size)
        for (packed in surface) {
            val state = statesByPosition[packed] ?: continue
            if (state.isAir) continue
            blocks += PreviewBlockInfo(pos = BlockPos.fromLong(packed), state = state)
        }
        if (blocks.isEmpty()) return null

        val previewView = AxionBlockTessellator.PreviewBlockRenderView(world, statesByPosition)
        val sectionOriginX = (ChunkedBooleanStore.sectionX(sectionKey) shl 4).toDouble()
        val sectionOriginY = (ChunkedBooleanStore.sectionY(sectionKey) shl 4).toDouble()
        val sectionOriginZ = (ChunkedBooleanStore.sectionZ(sectionKey) shl 4).toDouble()

        val layer = RenderLayerCompat.blockTranslucentCull()
        val allocator = BufferAllocator(layer.expectedBufferSize)
        val bufferBuilder = BufferBuilder(allocator, layer.drawMode, layer.vertexFormat)
        AxionBlockTessellator.tessellateBatch(
            blocks = blocks,
            world = previewView,
            matrixStack = MatrixStack(),
            consumer = bufferBuilder,
            cameraX = sectionOriginX,
            cameraY = sectionOriginY,
            cameraZ = sectionOriginZ,
            checkSides = true,
        )
        return bufferBuilder.endNullable()
    }

    private fun renderLegacy(
        context: AxionWorldRenderContext,
        world: ClientWorld,
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = CameraAccess.getPos(camera)
        val consumer = TintedAlphaVertexConsumer(
            context.consumers().getBuffer(RenderLayerCompat.blockTranslucentCull()),
            alpha / 255.0f, color,
        )
        val matrices = context.matrices()
        val statesView = states.asMap()
        val previewView = AxionBlockTessellator.PreviewBlockRenderView(world, statesView)

        val blocks = ArrayList<PreviewBlockInfo>(64)
        for (sectionKey in chunkBuffers.keys) {
            blocks.clear()
            val surface = ChunkMeshTessellator.buildSectionSurface(store, sectionKey, statesView)
            for (packed in surface) {
                val state = statesView[packed] ?: continue
                if (state.isAir) continue
                blocks += PreviewBlockInfo(pos = BlockPos.fromLong(packed), state = state)
            }
            if (blocks.isNotEmpty()) {
                AxionBlockTessellator.tessellateBatch(
                    blocks = blocks,
                    world = previewView,
                    matrixStack = matrices,
                    consumer = consumer,
                    cameraX = cameraPos.x - translationDelta.x,
                    cameraY = cameraPos.y - translationDelta.y,
                    cameraZ = cameraPos.z - translationDelta.z,
                    checkSides = true,
                )
            }
        }
    }

    private fun computeSignature(clipboard: ClipboardBuffer, origins: Collection<BlockPos>): Long {
        var sig = System.identityHashCode(clipboard).toLong()
        origins.forEach { sig = sig * 31L + it.asLong() }
        return if (sig == 0L) 1L else sig
    }

    private inline fun iterateAllCellsInSection(
        store: ChunkedBooleanStore,
        sectionKey: Long,
        action: (packed: Long) -> Unit,
    ) {
        val section = store.rawSection(sectionKey) ?: return
        val baseX = ChunkedBooleanStore.sectionX(sectionKey) shl 4
        val baseY = ChunkedBooleanStore.sectionY(sectionKey) shl 4
        val baseZ = ChunkedBooleanStore.sectionZ(sectionKey) shl 4
        for (z in 0..15) {
            for (y in 0..15) {
                val v = section[y + z * 16].toInt()
                if (v == 0) continue
                for (x in 0..15) {
                    if ((v and (1 shl x)) != 0) {
                        action(BlockPos.asLong(baseX + x, baseY + y, baseZ + z))
                    }
                }
            }
        }
    }
}

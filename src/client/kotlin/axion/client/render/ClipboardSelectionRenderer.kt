package axion.client.render

import axion.client.selection.SelectionBounds
import axion.common.model.BlockRegion
import axion.common.model.ClipboardCell
import axion.common.model.ClipboardBuffer
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import java.util.WeakHashMap

object ClipboardSelectionRenderer {
    private const val BASE_OVERLAY_SCALE: Float = 0.996f
    private const val PULSE_OVERLAY_SCALE: Float = 0.992f
    private const val MAX_BASE_OVERLAY_CELLS: Int = 4096
    private const val MAX_PULSE_OVERLAY_CELLS: Int = 2048
    private const val SELECTION_BASE_FILL_COLOR: Int = 0xFFCC5656.toInt()
    private const val SELECTION_BASE_FILL_ALPHA: Int = 1
    private const val SELECTION_PULSE_FILL_COLOR: Int = 0xFF7C98FF.toInt()
    private const val SELECTION_PULSE_MIN_ALPHA: Int = 4
    private const val SELECTION_PULSE_MAX_ALPHA: Int = 8
    private const val STATIC_FILL_ALPHA: Int = 52
    private val geometryCache = WeakHashMap<ClipboardBuffer, CachedGeometry>()
    private val sparseClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val surfaceClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val redGlassClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val blueGlassClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val grayGlassClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val surfaceCellCache = WeakHashMap<ClipboardBuffer, List<ClipboardCell>>()

    private data class CachedGeometry(
        val shape: VoxelShape,
        val boxes: List<Box>,
    )

    fun renderStaticSelection(
        context: AxionWorldRenderContext,
        origin: BlockPos,
        clipboard: ClipboardBuffer,
        outlineColor: Int,
        lineWidth: Float,
        fillColor: Int,
        fillAlpha: Int = STATIC_FILL_ALPHA,
    ): Boolean {
        return renderSelectionAtOrigins(
            context = context,
            origins = listOf(origin),
            clipboard = clipboard,
            outlineColor = outlineColor,
            lineWidth = lineWidth,
            baseFillColor = fillColor,
            baseAlpha = fillAlpha,
            pulseFillColor = null,
            pulseMinAlpha = 0,
            pulseMaxAlpha = 0,
        )
    }

    fun renderSelection(
        context: AxionWorldRenderContext,
        origin: BlockPos,
        clipboard: ClipboardBuffer,
        outlineColor: Int,
        lineWidth: Float,
    ): Boolean {
        return renderSelectionAtOrigins(
            context = context,
            origins = listOf(origin),
            clipboard = clipboard,
            outlineColor = outlineColor,
            lineWidth = lineWidth,
            baseFillColor = SELECTION_BASE_FILL_COLOR,
            baseAlpha = SELECTION_BASE_FILL_ALPHA,
            pulseFillColor = SELECTION_PULSE_FILL_COLOR,
            pulseMinAlpha = SELECTION_PULSE_MIN_ALPHA,
            pulseMaxAlpha = SELECTION_PULSE_MAX_ALPHA,
        )
    }

    fun renderSelection(
        context: AxionWorldRenderContext,
        origins: Collection<BlockPos>,
        clipboard: ClipboardBuffer,
        outlineColor: Int,
        lineWidth: Float,
    ): Boolean {
        return renderStyledSelection(
            context = context,
            origins = origins,
            clipboard = clipboard,
            outlineColor = outlineColor,
            lineWidth = lineWidth,
            baseFillColor = SELECTION_BASE_FILL_COLOR,
            baseAlpha = SELECTION_BASE_FILL_ALPHA,
            pulseFillColor = SELECTION_PULSE_FILL_COLOR,
            pulseMinAlpha = SELECTION_PULSE_MIN_ALPHA,
            pulseMaxAlpha = SELECTION_PULSE_MAX_ALPHA,
        )
    }

    fun renderStyledSelection(
        context: AxionWorldRenderContext,
        origins: Collection<BlockPos>,
        clipboard: ClipboardBuffer,
        outlineColor: Int,
        lineWidth: Float,
        baseFillColor: Int,
        baseAlpha: Int,
        pulseFillColor: Int?,
        pulseMinAlpha: Int,
        pulseMaxAlpha: Int,
    ): Boolean {
        return renderSelectionAtOrigins(
            context = context,
            origins = origins,
            clipboard = clipboard,
            outlineColor = outlineColor,
            lineWidth = lineWidth,
            baseFillColor = baseFillColor,
            baseAlpha = baseAlpha,
            pulseFillColor = pulseFillColor,
            pulseMinAlpha = pulseMinAlpha,
            pulseMaxAlpha = pulseMaxAlpha,
        )
    }

    fun renderPulse(
        context: AxionWorldRenderContext,
        origin: BlockPos,
        region: BlockRegion,
        clipboard: ClipboardBuffer,
        outlineColor: Int,
        lineWidth: Float,
        minAlpha: Int,
        maxAlpha: Int,
    ): Boolean {
        if (!isSparse(region, clipboard)) {
            return false
        }

        clipboard.cells.forEach { cell ->
            PulsingCuboidRenderer.renderShell(
                context = context,
                box = SelectionBounds.blockBox(origin.add(cell.offset)),
                outlineColor = outlineColor,
                lineWidth = lineWidth,
                minAlpha = minAlpha,
                maxAlpha = maxAlpha,
            )
        }
        return true
    }

    // Limit for CPU-intensive outline rendering - beyond this we use fast path
    private const val MAX_OUTLINE_POSITIONS: Int = 256

    fun renderPulsePositions(
        context: AxionWorldRenderContext,
        positions: Collection<BlockPos>,
        outlineColor: Int,
        lineWidth: Float,
        minAlpha: Int,
        maxAlpha: Int,
    ): Boolean {
        if (positions.isEmpty()) {
            return false
        }

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return false
        val cameraPos = camera.cameraPos
        val consumers = context.consumers()
        val matrixStack = context.matrices()
        val fillLayer = RenderLayerCompat.debugQuads()
        val fillConsumer = consumers.getBuffer(fillLayer)

        // Fast path: render simple boxes without expensive VoxelShape union for large selections
        val useFastPath = positions.size > MAX_OUTLINE_POSITIONS

        positions.forEach { pos ->
            val box = SelectionBounds.blockBox(pos)
            PulsingCuboidRenderer.renderFilledBox(
                matrixStack = matrixStack,
                consumer = fillConsumer,
                layer = fillLayer,
                cameraPos = cameraPos,
                box = box,
                alpha = minAlpha,
                color = outlineColor,
            )
            PulsingCuboidRenderer.renderFilledBox(
                matrixStack = matrixStack,
                consumer = fillConsumer,
                layer = fillLayer,
                cameraPos = cameraPos,
                box = box,
                alpha = PulsingCuboidRenderer.pulsingAlpha(minAlpha, maxAlpha),
                color = 0xFF7C98FF.toInt(),
            )
        }

        // Only build expensive VoxelShape for outline if under the limit
        if (!useFastPath) {
            var shape: VoxelShape = VoxelShapes.empty()
            positions.forEach { pos ->
                shape = VoxelShapes.union(shape, VoxelShapes.cuboid(SelectionBounds.blockBox(pos)))
            }

            VertexRenderingCompat.drawOutline(
                matrixStack,
                consumers.getBuffer(RenderLayerCompat.lines()),
                shape,
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z,
                outlineColor,
                lineWidth,
            )
        } else {
            // Fast path: draw individual block outlines without VoxelShape union
            val lineConsumer = consumers.getBuffer(RenderLayerCompat.lines())
            positions.forEach { pos ->
                VertexRenderingCompat.drawOutline(
                    matrixStack,
                    lineConsumer,
                    VoxelShapes.cuboid(SelectionBounds.blockBox(pos)),
                    -cameraPos.x,
                    -cameraPos.y,
                    -cameraPos.z,
                    outlineColor,
                    lineWidth,
                )
            }
        }
        return true
    }

    fun isSparse(region: BlockRegion, clipboard: ClipboardBuffer): Boolean {
        val size = region.normalized().size()
        return (size.x * size.y * size.z) != clipboard.nonAirCells().size
    }

    fun sparseClipboard(source: ClipboardBuffer): ClipboardBuffer {
        return sparseClipboardCache.getOrPut(source) {
            val nonAir = source.nonAirCells()
            if (nonAir.size == source.cells.size) source else ClipboardBuffer(size = source.size, cells = nonAir)
        }
    }

    fun surfaceClipboard(source: ClipboardBuffer): ClipboardBuffer {
        return surfaceClipboardCache.getOrPut(source) {
            val occupiedCells = source.nonAirCells()
            val surface = surfaceCells(source)
            if (surface.size == occupiedCells.size) source else ClipboardBuffer(size = source.size, cells = surface)
        }
    }

    private fun renderSelectionAtOrigins(
        context: AxionWorldRenderContext,
        origins: Collection<BlockPos>,
        clipboard: ClipboardBuffer,
        outlineColor: Int,
        lineWidth: Float,
        baseFillColor: Int,
        baseAlpha: Int,
        pulseFillColor: Int?,
        pulseMinAlpha: Int,
        pulseMaxAlpha: Int,
    ): Boolean {
        if (origins.isEmpty() || clipboard.cells.isEmpty()) {
            return false
        }

        val geometry = geometryFor(clipboard)
        val matrixStack = context.matrices()
        val consumers = context.consumers()
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return false
        val cameraPos = camera.cameraPos
        val overlayCellCount = geometry.boxes.size.toLong() * origins.size.toLong()
        val renderBaseOverlay = overlayCellCount <= MAX_BASE_OVERLAY_CELLS.toLong()
        val renderPulseOverlay = overlayCellCount <= MAX_PULSE_OVERLAY_CELLS.toLong()
        val baseOverlay = if (renderBaseOverlay) {
            overlayClipboard(clipboard, glassStateFor(baseFillColor), surfaceOnly = true)
        } else {
            null
        }
        val pulseAlpha = if (pulseFillColor != null) {
            PulsingCuboidRenderer.pulsingAlpha(
                minAlpha = pulseMinAlpha,
                maxAlpha = pulseMaxAlpha,
                periodMillis = 3200.0,
            )
        } else {
            0
        }
        val pulseOverlay = if (renderPulseOverlay) {
            pulseFillColor?.let { overlayClipboard(clipboard, glassStateFor(it), surfaceOnly = true) }
        } else {
            null
        }

        if (baseOverlay != null) {
            GhostBlockPreviewRenderer.render(
                context = context,
                clipboard = baseOverlay,
                origins = origins,
                alpha = baseAlpha,
                textured = true,
                scale = BASE_OVERLAY_SCALE,
            )
        }
        if (pulseOverlay != null) {
            GhostBlockPreviewRenderer.render(
                context = context,
                clipboard = pulseOverlay,
                origins = origins,
                alpha = pulseAlpha,
                textured = true,
                scale = PULSE_OVERLAY_SCALE,
            )
        }

        origins.forEach { origin ->
            val translatedShape = VoxelShapes.empty().let {
                // outline remains merged, but translated per origin
                geometry.shape.offset(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())
            }
            VertexRenderingCompat.drawOutline(
                matrixStack,
                consumers.getBuffer(RenderLayerCompat.lines()),
                translatedShape,
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z,
                outlineColor,
                lineWidth,
            )
        }

        return true
    }

    private fun geometryFor(clipboard: ClipboardBuffer): CachedGeometry {
        return geometryCache.getOrPut(clipboard) {
            var shape: VoxelShape = VoxelShapes.empty()
            val visibleCells = surfaceCells(clipboard)
            val boxes = ArrayList<Box>(visibleCells.size)
            visibleCells.forEach { cell ->
                val box = SelectionBounds.blockBox(BlockPos.ORIGIN.add(cell.offset))
                boxes += box
                shape = VoxelShapes.union(shape, VoxelShapes.cuboid(box))
            }
            CachedGeometry(shape = shape, boxes = boxes)
        }
    }

    private fun overlayClipboard(source: ClipboardBuffer, state: BlockState, surfaceOnly: Boolean): ClipboardBuffer {
        val cache = when (state.block) {
            Blocks.RED_STAINED_GLASS -> redGlassClipboardCache
            Blocks.LIGHT_BLUE_STAINED_GLASS -> blueGlassClipboardCache
            else -> grayGlassClipboardCache
        }
        return cache.getOrPut(source) {
            val cells = if (surfaceOnly) surfaceCells(source) else source.cells
            ClipboardBuffer(
                size = source.size,
                cells = cells.map { cell -> cell.copy(state = state) },
            )
        }
    }

    private fun surfaceCells(source: ClipboardBuffer): List<ClipboardCell> {
        return surfaceCellCache.getOrPut(source) {
            val occupiedCells = source.nonAirCells()
            if (occupiedCells.size <= 1) {
                occupiedCells
            } else {
                val occupied = occupiedCells.asSequence()
                    .map { Triple(it.offset.x, it.offset.y, it.offset.z) }
                    .toHashSet()
                occupiedCells.filter { cell ->
                    val x = cell.offset.x
                    val y = cell.offset.y
                    val z = cell.offset.z
                    listOf(
                        Triple(x - 1, y, z),
                        Triple(x + 1, y, z),
                        Triple(x, y - 1, z),
                        Triple(x, y + 1, z),
                        Triple(x, y, z - 1),
                        Triple(x, y, z + 1),
                    ).any { neighbor -> neighbor !in occupied }
                }
            }
        }
    }

    private fun glassStateFor(color: Int): BlockState {
        return when (color and 0x00FFFFFF) {
            0x00CC5656 -> Blocks.RED_STAINED_GLASS.defaultState
            0x007C98FF -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultState
            else -> Blocks.LIGHT_GRAY_STAINED_GLASS.defaultState
        }
    }
}

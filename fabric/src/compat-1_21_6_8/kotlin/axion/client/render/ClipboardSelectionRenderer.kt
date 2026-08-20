package axion.client.render
import axion.client.compat.CameraAccess

import axion.client.render.gpu.PreviewOcclusionCompat
import axion.client.render.gpu.PreviewOcclusionPolicy
import axion.client.selection.SelectionBounds
import axion.common.model.BlockRegion
import axion.common.model.ClipboardCell
import axion.common.model.ClipboardBuffer
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.Entry
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import java.util.WeakHashMap
import kotlin.math.roundToInt

object ClipboardSelectionRenderer {
    private const val BASE_OVERLAY_SCALE: Float = 0.996f
    private const val PULSE_OVERLAY_SCALE: Float = 0.992f
    private const val MAX_BASE_OVERLAY_CELLS: Int = 4096
    private const val MAX_PULSE_OVERLAY_CELLS: Int = 2048
    private const val SELECTION_BASE_FILL_COLOR: Int = 0xFFCC5656.toInt()
    private const val SELECTION_BASE_FILL_ALPHA: Int = 1
    private const val SELECTION_PULSE_FILL_COLOR: Int = 0xFF7C98FF.toInt()
    private const val SELECTION_PULSE_MIN_ALPHA: Int = PreviewVisualPolicy.PLACEMENT_PULSE_MIN_ALPHA
    // Peak alpha of the phased fill. The pulse now fades to a visible floor at
    // the crossover, so its peaks can be stronger than the old always-on stack
    // without the selection becoming muddy.
    private const val SELECTION_PULSE_MAX_ALPHA: Int = PreviewVisualPolicy.PLACEMENT_PULSE_MAX_ALPHA
    private const val STATIC_FILL_ALPHA: Int = 52
    private val geometryCache = WeakHashMap<ClipboardBuffer, CachedGeometry>()
    private val sparseClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val surfaceClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val redGlassClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val blueGlassClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val grayGlassClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val surfaceCellCache = WeakHashMap<ClipboardBuffer, List<ClipboardCell>>()
    private val lineWidthMethod: java.lang.reflect.Method? = runCatching {
        VertexConsumer::class.java.getMethod("lineWidth", Float::class.javaPrimitiveType)
    }.getOrNull()

    // VoxelShapes.union becomes very expensive when magic-select accumulates
    // separated blobs. Keep the single-shape path small, then switch to a
    // linear boundary-edge builder so multiple middle-click blobs do not
    // rebuild one giant outline synchronously on the render thread.
    //
    // Above this, build exact merged boundary edges instead of using
    // VoxelShapes.union. That preserves the selected block set without the
    // visible per-block segmentation or O(n^2) shape merge cost.
    private const val MAX_SINGLE_SHAPE_UNION_CELLS: Int = 256

    private data class CachedGeometry(
        val shape: VoxelShape?,
        val boxes: List<Box>,
        val componentOutlines: List<ComponentOutline>,
    )

    private sealed class ComponentOutline {
        data class Merged(val shape: VoxelShape) : ComponentOutline()
        data class BoundaryEdges(val edges: List<BoundaryEdge>) : ComponentOutline()
    }

    private data class BoundaryEdge(
        val x1: Int,
        val y1: Int,
        val z1: Int,
        val x2: Int,
        val y2: Int,
        val z2: Int,
    )

    private data class PlaneEdge(
        val x1: Int,
        val y1: Int,
        val z1: Int,
        val x2: Int,
        val y2: Int,
        val z2: Int,
        val normalAxis: Int,
        val normalSign: Int,
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
        drawContourOutline: Boolean = true,
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
            drawContourOutline = drawContourOutline,
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
        val cameraPos = CameraAccess.getPos(camera)
        val consumers = context.consumers()
        val matrixStack = context.matrices()
        val fillLayer = RenderLayerCompat.shaderSafeQuads()
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
        val nonAir = source.nonAirCells()
        if (nonAir.size == source.cells.size) return source
        return sparseClipboardCache.getOrPut(source) {
            ClipboardBuffer(size = source.size, cells = nonAir)
        }
    }

    fun surfaceClipboard(source: ClipboardBuffer): ClipboardBuffer {
        val occupiedCells = source.nonAirCells()
        val surface = surfaceCells(source)
        if (surface.size == occupiedCells.size) return source
        return surfaceClipboardCache.getOrPut(source) {
            ClipboardBuffer(size = source.size, cells = surface)
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
        drawContourOutline: Boolean = true,
    ): Boolean {
        if (origins.isEmpty() || clipboard.cells.isEmpty()) {
            return false
        }

        val geometry = geometryFor(clipboard)
        val matrixStack = context.matrices()
        val consumers = context.consumers()
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return false
        val cameraPos = CameraAccess.getPos(camera)
        val overlayCellCount = geometry.boxes.size.toLong() * origins.size.toLong()
        val renderBaseOverlay = overlayCellCount <= MAX_BASE_OVERLAY_CELLS.toLong()
        val renderPulseOverlay = overlayCellCount <= MAX_PULSE_OVERLAY_CELLS.toLong()
        val baseOverlay = if (renderBaseOverlay) {
            overlayClipboard(clipboard, glassStateFor(baseFillColor), surfaceOnly = true)
        } else {
            null
        }
        // Same signed-phase model as the box fill: show one colour at a time and
        // fade to a visible floor, rather than stacking a constant base overlay
        // under a pulsing one. Halves the translucent surfaces the view looks
        // through, which is what made deep selections read as blurred.
        val overlayPhase = PulsingCuboidRenderer.selectionPulsePhase(
            PulsingCuboidRenderer.SELECTION_PULSE_PERIOD_MILLIS,
        )
        val phasedAlpha = if (pulseFillColor != null && pulseMaxAlpha > 0) {
            PreviewVisualPolicy.pulseAlpha(maxOf(baseAlpha, pulseMinAlpha), pulseMaxAlpha, overlayPhase).roundToInt()
        } else baseAlpha
        val pulseAlpha = if (pulseFillColor != null && overlayPhase >= 0f) phasedAlpha else 0
        val phasedBaseAlpha = if (pulseFillColor == null || overlayPhase < 0f) phasedAlpha else 0
        val pulseOverlay = if (renderPulseOverlay) {
            pulseFillColor?.let { overlayClipboard(clipboard, glassStateFor(it), surfaceOnly = true) }
        } else {
            null
        }

        if (baseOverlay != null && phasedBaseAlpha > 0) {
            GhostBlockPreviewRenderer.render(
                context = context,
                clipboard = baseOverlay,
                origins = origins,
                alpha = phasedBaseAlpha,
                textured = true,
                scale = BASE_OVERLAY_SCALE,
                sessionTag = "selection-base",
            )
        }
        if (pulseOverlay != null && pulseAlpha > 0) {
            GhostBlockPreviewRenderer.render(
                context = context,
                clipboard = pulseOverlay,
                origins = origins,
                alpha = pulseAlpha,
                textured = true,
                scale = PULSE_OVERLAY_SCALE,
                sessionTag = "selection-pulse",
            )
        }

        // A region selection keeps its clean bounding outline and only takes the
        // contour *fill* from here; tracing every block edge as well would be
        // noise. Magic Select has no bounding box, so it still outlines contours.
        if (!drawContourOutline) return true

        val lineConsumer = consumers.getBuffer(RenderLayerCompat.lines())
        val mergedShape = geometry.shape
        if (mergedShape != null) {
            // Small selection: use merged VoxelShape for smooth outline
            origins.forEach { origin ->
                val translatedShape = mergedShape.offset(
                    origin.x.toDouble(),
                    origin.y.toDouble(),
                    origin.z.toDouble(),
                )
                VertexRenderingCompat.drawOutline(
                    matrixStack,
                    lineConsumer,
                    translatedShape,
                    -cameraPos.x,
                    -cameraPos.y,
                    -cameraPos.z,
                    outlineColor,
                    lineWidth,
                )
            }
        } else {
            // Large selection: render per-component outlines
            val outlines = geometry.componentOutlines
            if (outlines.isNotEmpty()) {
                origins.forEach { origin ->
                    val ox = origin.x.toDouble()
                    val oy = origin.y.toDouble()
                    val oz = origin.z.toDouble()
                    outlines.forEach { outline ->
                        when (outline) {
                            is ComponentOutline.Merged -> {
                                val translatedShape = outline.shape.offset(ox, oy, oz)
                                VertexRenderingCompat.drawOutline(
                                    matrixStack,
                                    lineConsumer,
                                    translatedShape,
                                    -cameraPos.x,
                                    -cameraPos.y,
                                    -cameraPos.z,
                                    outlineColor,
                                    lineWidth,
                                )
                            }

                            is ComponentOutline.BoundaryEdges -> {
                                renderBoundaryEdges(
                                    matrixStack = matrixStack,
                                    consumer = lineConsumer,
                                    edges = outline.edges,
                                    originX = ox,
                                    originY = oy,
                                    originZ = oz,
                                    cameraX = cameraPos.x,
                                    cameraY = cameraPos.y,
                                    cameraZ = cameraPos.z,
                                    color = outlineColor,
                                    lineWidth = lineWidth,
                                )
                            }
                        }
                    }
                }
            }
        }

        return true
    }

    private fun geometryFor(clipboard: ClipboardBuffer): CachedGeometry {
        return geometryCache.getOrPut(clipboard) {
            val visibleCells = surfaceCells(clipboard)
            val boxes = ArrayList<Box>(visibleCells.size)
            visibleCells.forEach { cell ->
                boxes += SelectionBounds.blockBox(BlockPos.ORIGIN.add(cell.offset))
            }
            if (visibleCells.size <= MAX_SINGLE_SHAPE_UNION_CELLS) {
                // Small selection: build merged VoxelShape for smooth outline
                var shape: VoxelShape = VoxelShapes.empty()
                boxes.forEach { box ->
                    shape = VoxelShapes.union(shape, VoxelShapes.cuboid(box))
                }
                CachedGeometry(shape = shape, boxes = boxes, componentOutlines = emptyList())
            } else {
                CachedGeometry(
                    shape = null,
                    boxes = boxes,
                    componentOutlines = listOf(ComponentOutline.BoundaryEdges(computeBoundaryEdges(clipboard))),
                )
            }
        }
    }

    private fun computeBoundaryEdges(clipboard: ClipboardBuffer): List<BoundaryEdge> {
        val nonAirCells = clipboard.nonAirCells()
        if (nonAirCells.isEmpty()) return emptyList()

        val occupied = HashSet<Long>(nonAirCells.size)
        nonAirCells.forEach { cell ->
            occupied.add(BlockPos.asLong(cell.offset.x, cell.offset.y, cell.offset.z))
        }

        val edgeSet = LinkedHashSet<PlaneEdge>()
        nonAirCells.forEach { cell ->
            val x = cell.offset.x
            val y = cell.offset.y
            val z = cell.offset.z

            if (BlockPos.asLong(x - 1, y, z) !in occupied) {
                addFaceEdges(edgeSet, x, y, z, x, y + 1, z, x, y + 1, z + 1, x, y, z + 1, normalAxis = 0, normalSign = -1)
            }
            if (BlockPos.asLong(x + 1, y, z) !in occupied) {
                addFaceEdges(edgeSet, x + 1, y, z, x + 1, y, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z, normalAxis = 0, normalSign = 1)
            }
            if (BlockPos.asLong(x, y - 1, z) !in occupied) {
                addFaceEdges(edgeSet, x, y, z, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z, normalAxis = 1, normalSign = -1)
            }
            if (BlockPos.asLong(x, y + 1, z) !in occupied) {
                addFaceEdges(edgeSet, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1, normalAxis = 1, normalSign = 1)
            }
            if (BlockPos.asLong(x, y, z - 1) !in occupied) {
                addFaceEdges(edgeSet, x, y, z, x + 1, y, z, x + 1, y + 1, z, x, y + 1, z, normalAxis = 2, normalSign = -1)
            }
            if (BlockPos.asLong(x, y, z + 1) !in occupied) {
                addFaceEdges(edgeSet, x, y, z + 1, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y, z + 1, normalAxis = 2, normalSign = 1)
            }
        }

        val boundaryEdges = LinkedHashSet<BoundaryEdge>(edgeSet.size)
        edgeSet.forEach { edge ->
            boundaryEdges.add(BoundaryEdge(edge.x1, edge.y1, edge.z1, edge.x2, edge.y2, edge.z2))
        }
        return boundaryEdges.toList()
    }

    private fun addFaceEdges(
        edges: MutableSet<PlaneEdge>,
        ax: Int,
        ay: Int,
        az: Int,
        bx: Int,
        by: Int,
        bz: Int,
        cx: Int,
        cy: Int,
        cz: Int,
        dx: Int,
        dy: Int,
        dz: Int,
        normalAxis: Int,
        normalSign: Int,
    ) {
        togglePlaneEdge(edges, ax, ay, az, bx, by, bz, normalAxis, normalSign)
        togglePlaneEdge(edges, bx, by, bz, cx, cy, cz, normalAxis, normalSign)
        togglePlaneEdge(edges, cx, cy, cz, dx, dy, dz, normalAxis, normalSign)
        togglePlaneEdge(edges, dx, dy, dz, ax, ay, az, normalAxis, normalSign)
    }

    private fun togglePlaneEdge(
        edges: MutableSet<PlaneEdge>,
        ax: Int,
        ay: Int,
        az: Int,
        bx: Int,
        by: Int,
        bz: Int,
        normalAxis: Int,
        normalSign: Int,
    ) {
        val edge = if (isBeforeOrSame(ax, ay, az, bx, by, bz)) {
            PlaneEdge(ax, ay, az, bx, by, bz, normalAxis, normalSign)
        } else {
            PlaneEdge(bx, by, bz, ax, ay, az, normalAxis, normalSign)
        }
        if (!edges.remove(edge)) {
            edges.add(edge)
        }
    }

    private fun isBeforeOrSame(ax: Int, ay: Int, az: Int, bx: Int, by: Int, bz: Int): Boolean {
        return ax < bx ||
            (ax == bx && ay < by) ||
            (ax == bx && ay == by && az <= bz)
    }

    private fun renderBoundaryEdges(
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        edges: List<BoundaryEdge>,
        originX: Double,
        originY: Double,
        originZ: Double,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        color: Int,
        lineWidth: Float,
    ) {
        val entry = matrixStack.peek()
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val alpha = (color ushr 24) and 0xFF

        edges.forEach { edge ->
            val x1 = (originX + edge.x1 - cameraX).toFloat()
            val y1 = (originY + edge.y1 - cameraY).toFloat()
            val z1 = (originZ + edge.z1 - cameraZ).toFloat()
            val x2 = (originX + edge.x2 - cameraX).toFloat()
            val y2 = (originY + edge.y2 - cameraY).toFloat()
            val z2 = (originZ + edge.z2 - cameraZ).toFloat()
            val normalX = (edge.x2 - edge.x1).coerceIn(-1, 1).toFloat()
            val normalY = (edge.y2 - edge.y1).coerceIn(-1, 1).toFloat()
            val normalZ = (edge.z2 - edge.z1).coerceIn(-1, 1).toFloat()

            emitLineVertex(consumer, entry, x1, y1, z1, red, green, blue, alpha, normalX, normalY, normalZ, lineWidth)
            emitLineVertex(consumer, entry, x2, y2, z2, red, green, blue, alpha, normalX, normalY, normalZ, lineWidth)
        }
    }

    private fun emitLineVertex(
        consumer: VertexConsumer,
        entry: Entry,
        x: Float,
        y: Float,
        z: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        lineWidth: Float,
    ) {
        val vertex = consumer
            .vertex(entry, x, y, z)
            .color(red, green, blue, alpha)
            .normal(entry, normalX, normalY, normalZ)
        lineWidthMethod?.invoke(vertex, lineWidth)
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
                val statesByOffset = occupiedCells.associate { cell ->
                    BlockPos.asLong(cell.offset.x, cell.offset.y, cell.offset.z) to cell.state
                }
                occupiedCells.filter { cell ->
                    Direction.entries.any { face ->
                        val neighbor = statesByOffset[BlockPos.asLong(
                            cell.offset.x + face.offsetX,
                            cell.offset.y + face.offsetY,
                            cell.offset.z + face.offsetZ,
                        )]
                        PreviewOcclusionPolicy.isFaceExposed(neighbor, PreviewOcclusionCompat::isOpaqueFullCube)
                    }
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

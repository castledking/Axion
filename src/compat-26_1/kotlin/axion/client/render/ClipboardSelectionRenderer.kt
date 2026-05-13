package axion.client.render
import axion.client.compat.CameraAccess

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
import net.minecraft.core.Vec3i
import axion.client.compat.add
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import java.util.WeakHashMap

object ClipboardSelectionRenderer {
    private const val MAX_SINGLE_SHAPE_UNION_CELLS: Int = 256
    private const val SELECTION_BASE_FILL_COLOR: Int = 0xFFCC5656.toInt()
    private const val SELECTION_BASE_FILL_ALPHA: Int = 1
    private const val SELECTION_PULSE_FILL_COLOR: Int = 0xFF7C98FF.toInt()
    private const val SELECTION_PULSE_MIN_ALPHA: Int = 4
    private const val SELECTION_PULSE_MAX_ALPHA: Int = 8
    private const val STATIC_FILL_ALPHA: Int = 52
    private const val BASE_OVERLAY_SCALE: Float = 0.996f
    private const val PULSE_OVERLAY_SCALE: Float = 0.992f
    private const val MAX_BASE_OVERLAY_CELLS: Int = 4096
    private const val MAX_PULSE_OVERLAY_CELLS: Int = 2048
    private val geometryCache = WeakHashMap<ClipboardBuffer, CachedGeometry>()
    private val sparseClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val surfaceClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val surfaceCellCache = WeakHashMap<ClipboardBuffer, List<ClipboardCell>>()
    private val lineWidthMethod: java.lang.reflect.Method? = runCatching {
        VertexConsumer::class.java.getMethod("lineWidth", Float::class.javaPrimitiveType)
    }.getOrNull()

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
        val x1: Int, val y1: Int, val z1: Int,
        val x2: Int, val y2: Int, val z2: Int,
    )

    private data class PlaneEdge(
        val x1: Int, val y1: Int, val z1: Int,
        val x2: Int, val y2: Int, val z2: Int,
        val normalAxis: Int, val normalSign: Int,
    )

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
        val cameraPos = CameraAccess.getPos(camera)

        val offsetX = if (context.needsCameraOffset()) -cameraPos.x else 0.0
        val offsetY = if (context.needsCameraOffset()) -cameraPos.y else 0.0
        val offsetZ = if (context.needsCameraOffset()) -cameraPos.z else 0.0

        val lineConsumer = consumers.getBuffer(RenderLayerCompat.lines())
        val mergedShape = geometry.shape
        if (mergedShape != null) {
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
                    offsetX,
                    offsetY,
                    offsetZ,
                    outlineColor,
                    lineWidth,
                )
            }
        } else {
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
                                    offsetX,
                                    offsetY,
                                    offsetZ,
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
                                    cameraX = if (context.needsCameraOffset()) cameraPos.x else 0.0,
                                    cameraY = if (context.needsCameraOffset()) cameraPos.y else 0.0,
                                    cameraZ = if (context.needsCameraOffset()) cameraPos.z else 0.0,
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
                boxes += SelectionBounds.blockBox(BlockPos(0, 0, 0).add(cell.offset))
            }
            if (visibleCells.size <= MAX_SINGLE_SHAPE_UNION_CELLS) {
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

            if (BlockPos.asLong(x - 1, y, z) !in occupied) addFaceEdges(edgeSet, x, y, z, x, y + 1, z, x, y + 1, z + 1, x, y, z + 1, 0, -1)
            if (BlockPos.asLong(x + 1, y, z) !in occupied) addFaceEdges(edgeSet, x + 1, y, z, x + 1, y, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z, 0, 1)
            if (BlockPos.asLong(x, y - 1, z) !in occupied) addFaceEdges(edgeSet, x, y, z, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z, 1, -1)
            if (BlockPos.asLong(x, y + 1, z) !in occupied) addFaceEdges(edgeSet, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1, 1, 1)
            if (BlockPos.asLong(x, y, z - 1) !in occupied) addFaceEdges(edgeSet, x, y, z, x + 1, y, z, x + 1, y + 1, z, x, y + 1, z, 2, -1)
            if (BlockPos.asLong(x, y, z + 1) !in occupied) addFaceEdges(edgeSet, x, y, z + 1, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y, z + 1, 2, 1)
        }

        val boundaryEdges = LinkedHashSet<BoundaryEdge>(edgeSet.size)
        edgeSet.forEach { edge -> boundaryEdges.add(BoundaryEdge(edge.x1, edge.y1, edge.z1, edge.x2, edge.y2, edge.z2)) }
        return boundaryEdges.toList()
    }

    private fun addFaceEdges(
        edges: MutableSet<PlaneEdge>,
        ax: Int, ay: Int, az: Int,
        bx: Int, by: Int, bz: Int,
        cx: Int, cy: Int, cz: Int,
        dx: Int, dy: Int, dz: Int,
        normalAxis: Int, normalSign: Int,
    ) {
        togglePlaneEdge(edges, ax, ay, az, bx, by, bz, normalAxis, normalSign)
        togglePlaneEdge(edges, bx, by, bz, cx, cy, cz, normalAxis, normalSign)
        togglePlaneEdge(edges, cx, cy, cz, dx, dy, dz, normalAxis, normalSign)
        togglePlaneEdge(edges, dx, dy, dz, ax, ay, az, normalAxis, normalSign)
    }

    private fun togglePlaneEdge(
        edges: MutableSet<PlaneEdge>,
        ax: Int, ay: Int, az: Int,
        bx: Int, by: Int, bz: Int,
        normalAxis: Int, normalSign: Int,
    ) {
        val edge = if (isBeforeOrSame(ax, ay, az, bx, by, bz)) {
            PlaneEdge(ax, ay, az, bx, by, bz, normalAxis, normalSign)
        } else {
            PlaneEdge(bx, by, bz, ax, ay, az, normalAxis, normalSign)
        }
        if (!edges.remove(edge)) edges.add(edge)
    }

    private fun isBeforeOrSame(ax: Int, ay: Int, az: Int, bx: Int, by: Int, bz: Int): Boolean {
        return ax < bx || (ax == bx && ay < by) || (ax == bx && ay == by && az <= bz)
    }

    private fun renderBoundaryEdges(
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        edges: List<BoundaryEdge>,
        originX: Double, originY: Double, originZ: Double,
        cameraX: Double, cameraY: Double, cameraZ: Double,
        color: Int, lineWidth: Float,
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
        consumer: VertexConsumer, entry: Entry,
        x: Float, y: Float, z: Float,
        red: Int, green: Int, blue: Int, alpha: Int,
        normalX: Float, normalY: Float, normalZ: Float,
        lineWidth: Float,
    ) {
        val vertex = consumer
            .vertex(entry, x, y, z)
            .color(red, green, blue, alpha)
            .normal(entry, normalX, normalY, normalZ)
        lineWidthMethod?.invoke(vertex, lineWidth)
    }

    private fun surfaceCells(source: ClipboardBuffer): List<ClipboardCell> {
        return surfaceCellCache.getOrPut(source) {
            val occupiedCells = source.nonAirCells()
            if (occupiedCells.size <= 1) {
                occupiedCells
            } else {
                val opaquePositions = occupiedCells.asSequence()
                    .filter { it.state.isOpaqueFullCube }
                    .map { BlockPos.asLong(it.offset.x, it.offset.y, it.offset.z) }
                    .toHashSet()
                occupiedCells.filter { cell ->
                    val x = cell.offset.x
                    val y = cell.offset.y
                    val z = cell.offset.z
                    BlockPos.asLong(x - 1, y, z) !in opaquePositions ||
                        BlockPos.asLong(x + 1, y, z) !in opaquePositions ||
                        BlockPos.asLong(x, y - 1, z) !in opaquePositions ||
                        BlockPos.asLong(x, y + 1, z) !in opaquePositions ||
                        BlockPos.asLong(x, y, z - 1) !in opaquePositions ||
                        BlockPos.asLong(x, y, z + 1) !in opaquePositions
                }
            }
        }
    }
}

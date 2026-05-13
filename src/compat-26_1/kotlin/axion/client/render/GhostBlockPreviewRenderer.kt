package axion.client.render

import axion.client.compat.CameraAccess
import axion.client.compat.VersionCompatImpl
import axion.client.selection.SelectionBounds
import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import axion.client.compat.add

object GhostBlockPreviewRenderer {
    private const val MAX_GHOST_BLOCKS: Int = 65536
    private const val MAX_TEXTURED_QUADS: Int = 8192
    private const val CHUNKED_PATH_CELL_THRESHOLD: Int = 32768

    fun maxOriginsFor(nonAirCellCount: Int): Int {
        if (nonAirCellCount <= 0) {
            return 0
        }
        return MAX_GHOST_BLOCKS / nonAirCellCount
    }

    fun render(
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int = 0xFFFFFFFF.toInt(),
        alpha: Int = 44,
        textured: Boolean = false,
        fullBlock: Boolean = false,
        scale: Float = 1.0f,
        sessionTag: String = "default",
        forceChunked: Boolean = false,
    ) {
        val cells = clipboard.nonAirCells()
        if (origins.isEmpty() || cells.isEmpty()) {
            return
        }

        if (textured) {
            val totalCells = cells.size.toLong() * origins.size.toLong()
            if (forceChunked || totalCells > CHUNKED_PATH_CELL_THRESHOLD) {
                val handled = VersionCompatImpl.renderChunkedPreview(
                    "ghost:$sessionTag",
                    context,
                    clipboard,
                    origins,
                    color,
                    alpha.coerceIn(0, 255),
                )
                if (handled) {
                    return
                }
            }
        }

        val maxOrigins = maxOriginsFor(cells.size)
        if (maxOrigins <= 0) {
            return
        }

        if (textured) {
            val region = ChunkedPreviewRegion.getOrBuild(
                clipboard = clipboard,
                origins = origins.take(maxOrigins),
                maxQuads = MAX_TEXTURED_QUADS,
            )
            val renderedTextured = PreviewBlockTessellator.render(
                context = context,
                region = region,
                color = color,
                alpha = alpha.coerceIn(0, 255),
            )
            if (renderedTextured) {
                return
            }
        }

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = CameraAccess.getPos(camera)
        val offset = if (context.needsCameraOffset()) cameraPos else Vec3d(0.0, 0.0, 0.0)
        val matrices = context.matrices()
        val layer = RenderLayerCompat.debugQuads()
        val consumer = context.consumers().getBuffer(layer)
        val fillAlpha = alpha.coerceIn(0, 255)

        origins.take(maxOrigins).forEach { origin ->
            cells.forEach { cell ->
                val pos = origin.add(cell.offset)
                val box = scaledBox(SelectionBounds.blockBox(pos), scale)
                PulsingCuboidRenderer.renderFilledBox(
                    matrixStack = matrices,
                    consumer = consumer,
                    layer = layer,
                    cameraPos = offset,
                    box = box,
                    alpha = fillAlpha,
                    color = color,
                )
            }
        }
    }

    private fun scaledBox(box: Box, scale: Float): Box {
        if (scale == 1.0f) {
            return box
        }
        val centerX = (box.minX + box.maxX) * 0.5
        val centerY = (box.minY + box.maxY) * 0.5
        val centerZ = (box.minZ + box.maxZ) * 0.5
        val halfX = (box.maxX - box.minX) * 0.5 * scale
        val halfY = (box.maxY - box.minY) * 0.5 * scale
        val halfZ = (box.maxZ - box.minZ) * 0.5 * scale
        return Box(
            centerX - halfX,
            centerY - halfY,
            centerZ - halfZ,
            centerX + halfX,
            centerY + halfY,
            centerZ + halfZ,
        )
    }
}

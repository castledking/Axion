package axion.client.render

import axion.client.compat.CameraAccess
import axion.client.compat.VersionCompatImpl
import axion.client.network.AxionServerConnection
import axion.client.selection.SelectionBounds
import axion.common.model.ClipboardBuffer
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import axion.client.compat.add

object GhostBlockPreviewRenderer {
    private const val MAX_GHOST_BLOCKS: Int = 65536
    private const val MAX_TEXTURED_QUADS: Int = 8192

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
        fallbackClipboard: ClipboardBuffer = ClipboardSelectionRenderer.surfaceClipboard(clipboard),
        color: Int = 0xFFFFFFFF.toInt(),
        alpha: Int = PreviewVisualPolicy.CULLED_DESTINATION_ALPHA,
        textured: Boolean = false,
        fullBlock: Boolean = false,
        scale: Float = 1.0f,
        sessionTag: String = "default",
        forceChunked: Boolean = false,
        allowChunked: Boolean = true,
    ) {
        // Destination ghosts used to be rewritten to opaque light-grey
        // concrete on a remote Axion session, purely so a translucent block's
        // texel alpha could not compound into the shell's policy alpha. The
        // shell shader now drops texel alpha itself
        // (PreviewVisualPolicy.IGNORE_TEXTURE_ALPHA_DEFINE), so the real
        // captured blocks can be shown. They are read from the client's own
        // world, so this reveals nothing the player is not already rendering.
        val renderClipboard = clipboard
        val renderFallbackClipboard = fallbackClipboard
        val occupiedCells = renderClipboard.nonAirCells()
        val fallbackCells = renderFallbackClipboard.nonAirCells()
        if (origins.isEmpty() || occupiedCells.isEmpty() || fallbackCells.isEmpty()) {
            return
        }

        if (textured) {
            if (allowChunked) {
                val handled = VersionCompatImpl.renderChunkedPreview(
                    "ghost:$sessionTag",
                    context,
                    renderClipboard,
                    renderFallbackClipboard,
                    origins,
                    color,
                    alpha.coerceIn(0, 255),
                    scale,
                )
                if (handled) {
                    return
                }
            }
        }

        val maxOrigins = maxOriginsFor(fallbackCells.size)
        if (maxOrigins <= 0) {
            return
        }

        if (textured) {
            val region = ChunkedPreviewRegion.getOrBuild(
                clipboard = renderClipboard,
                surfaceClipboard = renderFallbackClipboard,
                origins = origins.take(maxOrigins),
                maxQuads = MAX_TEXTURED_QUADS,
            )
            val renderedTextured = PreviewBlockTessellator.render(
                context = context,
                region = region,
                color = color,
                alpha = alpha.coerceIn(0, 255),
                ignoreTextureAlpha = PreviewVisualPolicy.ignoresTextureAlpha(sessionTag),
            )
            if (renderedTextured) {
                return
            }
        }

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera
        val cameraPos = CameraAccess.getPos(camera)
        val offset = if (context.needsCameraOffset()) cameraPos else Vec3d(0.0, 0.0, 0.0)
        val matrices = context.matrices()
        val layer = RenderLayerCompat.shaderSafeQuads()
        val consumer = context.consumers().getBuffer(layer)
        val fillAlpha = alpha.coerceIn(0, 255)

        origins.take(maxOrigins).forEach { origin ->
            fallbackCells.forEach { cell ->
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

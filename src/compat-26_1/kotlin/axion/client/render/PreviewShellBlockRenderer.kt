package axion.client.render

import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.BlockPos

object PreviewShellBlockRenderer {
    fun render(
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        surfaceClipboard: ClipboardBuffer = ClipboardSelectionRenderer.surfaceClipboard(clipboard),
        color: Int,
        alpha: Int,
        scale: Float = 1.0f,
    ): Boolean {
        if (origins.isEmpty() || clipboard.nonAirCells().isEmpty()) {
            return false
        }

        val region = ChunkedPreviewRegion.getOrBuild(
            clipboard = clipboard,
            surfaceClipboard = surfaceClipboard,
            origins = origins,
            maxQuads = 8192,
        )
        return PreviewBlockTessellator.render(
            context = context,
            region = region,
            color = color,
            alpha = alpha,
        )
    }
}

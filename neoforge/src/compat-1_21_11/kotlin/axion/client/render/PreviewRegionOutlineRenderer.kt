package axion.client.render
import axion.client.compat.CameraAccess

import axion.client.current.SelectionBounds
import axion.common.model.ClipboardBuffer
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

object PreviewRegionOutlineRenderer {
    const val MAX_REGION_QUADS: Int = 16384

    fun render(
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        outlineColor: Int,
        lineWidth: Float,
    ): Boolean {
        if (origins.isEmpty() || clipboard.cells.isEmpty()) {
            return false
        }

        val client = Minecraft.getInstance()
        val camera = client.gameRenderer.camera ?: return false
        val cameraPos = CameraAccess.getPos(camera)
        val region = ChunkedPreviewRegion.getOrBuild(
            clipboard = clipboard,
            origins = origins,
            maxQuads = MAX_REGION_QUADS,
        )
        region.chunks.values.forEach { chunk ->
            if (!chunk.shape.isEmpty) {
                VertexRenderingCompat.drawOutline(
                    context.matrices(),
                    context.consumers().getBuffer(RenderLayerCompat.lines()),
                    chunk.shape,
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
}

package axion.client.render

import axion.client.selection.SelectionBounds
import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos

object PreviewRegionOutlineRenderer {
    private const val MAX_REGION_QUADS: Int = 4096

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

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return false
        val cameraPos = camera.cameraPos
        val region = ChunkedPreviewRegion.getOrBuild(
            clipboard = clipboard,
            origins = origins,
            maxQuads = MAX_REGION_QUADS,
        )
        region.chunks.values.forEach { chunk ->
            if (!chunk.outlineShape.isEmpty) {
                VertexRenderingCompat.drawOutline(
                    context.matrices(),
                    context.consumers().getBuffer(RenderLayerCompat.lines()),
                    chunk.outlineShape,
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

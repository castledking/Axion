package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.tool.RepeatRegionPreview
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object RepeatPreviewRenderer {
    fun render(
        context: WorldRenderContext,
        preview: RepeatRegionPreview,
        destinationColor: Int,
        lineWidth: Float,
    ) {
        preview.destinationRegions.forEach { region ->
            PulsingCuboidRenderer.render(
                context = context,
                box = SelectionBounds.outlineBox(SelectionBounds.regionBox(region)),
                outlineColor = destinationColor,
                lineWidth = lineWidth,
            )
        }
        GhostBlockPreviewRenderer.render(
            context = context,
            clipboard = preview.clipboardBuffer,
            origins = preview.destinationRegions.map { it.minCorner() },
        )
    }
}

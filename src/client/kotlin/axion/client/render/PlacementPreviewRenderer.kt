package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.tool.PlacementToolController
import axion.client.tool.PlacementToolMode
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object PlacementPreviewRenderer {
    private const val MOVE_SOURCE_COLOR: Int = 0xFFFF7A7A.toInt()
    private const val CLONE_DESTINATION_COLOR: Int = 0xFFFFB347.toInt()
    private const val MOVE_DESTINATION_COLOR: Int = 0xFF7EE6A6.toInt()
    private const val LINE_WIDTH: Float = 1.75f
    private const val DEFAULT_GHOST_ALPHA: Int = 44
    private const val MOVE_SOURCE_GHOST_ALPHA: Int = 34
    private const val MOVE_DESTINATION_GHOST_ALPHA: Int = 52

    fun render(context: WorldRenderContext) {
        val preview = PlacementToolController.currentPreview() ?: return
        val destinationColor = when (preview.mode) {
            PlacementToolMode.CLONE -> CLONE_DESTINATION_COLOR
            PlacementToolMode.MOVE -> MOVE_DESTINATION_COLOR
        }
        if (preview.mode == PlacementToolMode.MOVE) {
            PulsingCuboidRenderer.render(
                context = context,
                box = SelectionBounds.outlineBox(SelectionBounds.regionBox(preview.sourceRegion)),
                outlineColor = MOVE_SOURCE_COLOR,
                lineWidth = LINE_WIDTH,
            )
            GhostBlockPreviewRenderer.render(
                context = context,
                clipboard = preview.sourceClipboardBuffer,
                origins = listOf(preview.sourceRegion.minCorner()),
                color = MOVE_SOURCE_COLOR,
                alpha = MOVE_SOURCE_GHOST_ALPHA,
            )
        }
        PulsingCuboidRenderer.render(
            context = context,
            box = SelectionBounds.outlineBox(SelectionBounds.regionBox(preview.destinationRegion)),
            outlineColor = destinationColor,
            lineWidth = LINE_WIDTH,
        )
        GhostBlockPreviewRenderer.render(
            context = context,
            clipboard = preview.destinationClipboardBuffer,
            origins = listOf(preview.destinationRegion.minCorner()),
            color = destinationColor,
            alpha = if (preview.mode == PlacementToolMode.MOVE) MOVE_DESTINATION_GHOST_ALPHA else DEFAULT_GHOST_ALPHA,
        )
    }
}

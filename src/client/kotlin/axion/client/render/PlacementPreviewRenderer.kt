package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.symmetry.SymmetryOperationExpander
import axion.client.tool.PlacementToolController
import axion.client.tool.PlacementCommitService
import axion.client.tool.PlacementToolMode
import axion.common.operation.CloneRegionOperation
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object PlacementPreviewRenderer {
    private const val CLONE_DESTINATION_COLOR: Int = 0xFFFFB347.toInt()
    private const val MOVE_DESTINATION_COLOR: Int = 0xFF7EE6A6.toInt()
    private const val LINE_WIDTH: Float = 1.75f

    fun render(context: WorldRenderContext) {
        val preview = PlacementToolController.currentPreview() ?: return
        val destinationColor = when (preview.mode) {
            PlacementToolMode.CLONE -> CLONE_DESTINATION_COLOR
            PlacementToolMode.MOVE -> MOVE_DESTINATION_COLOR
        }
        val cloneOperations = SymmetryOperationExpander.expand(PlacementCommitService.toOperation(preview))
            .filterIsInstance<CloneRegionOperation>()
        if (cloneOperations.isEmpty()) {
            return
        }

        val destinationRegions = cloneOperations.map { operation ->
            preview.sourceRegion.offset(operation.destinationOrigin.subtract(preview.sourceRegion.minCorner())).normalized()
        }
        destinationRegions.forEach { region ->
            PulsingCuboidRenderer.render(
                context = context,
                box = SelectionBounds.outlineBox(SelectionBounds.regionBox(region)),
                outlineColor = destinationColor,
                lineWidth = LINE_WIDTH,
            )
        }
        GhostBlockPreviewRenderer.render(
            context = context,
            clipboard = preview.clipboardBuffer,
            origins = cloneOperations.map { it.destinationOrigin },
        )
    }
}

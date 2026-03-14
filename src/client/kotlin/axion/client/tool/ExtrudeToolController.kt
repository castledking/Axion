package axion.client.tool

import axion.client.AxionClientState
import axion.client.selection.SelectionController
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.AxionSubtool
import axion.common.operation.ExtrudeMode
import net.minecraft.client.MinecraftClient

object ExtrudeToolController {
    private val dispatcher = SymmetryAwareOperationDispatcher()

    fun onEndTick(client: MinecraftClient) {
        if (!isExtrudeActive()) {
            reset()
            return
        }

        val world = client.world ?: run {
            reset()
            return
        }

        val preview = ExtrudePlacementService.createPreview(
            client = client,
            world = world,
            target = SelectionController.currentTarget(),
        )

        if (preview == null) {
            reset()
        } else {
            AxionClientState.updateExtrudeToolState(ExtrudeToolState.Previewing(preview))
        }
    }

    fun currentPreview(): ExtrudePreviewState? = when (val state = AxionClientState.extrudeToolState) {
        ExtrudeToolState.Idle -> null
        is ExtrudeToolState.Previewing -> state.preview
    }

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        if (!isExtrudeActive()) {
            return false
        }

        val preview = currentPreview() ?: return false
        dispatcher.dispatch(ExtrudeCommitService.toOperation(preview, ExtrudeMode.SHRINK))
        return true
    }

    fun handleSecondaryAction(client: MinecraftClient): Boolean {
        if (!isExtrudeActive()) {
            return false
        }

        val preview = currentPreview() ?: return false
        dispatcher.dispatch(ExtrudeCommitService.toOperation(preview, ExtrudeMode.EXTEND))
        return true
    }

    fun reset() {
        if (AxionClientState.extrudeToolState !is ExtrudeToolState.Idle) {
            AxionClientState.updateExtrudeToolState(ExtrudeToolState.Idle)
        }
    }

    private fun isExtrudeActive(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive() &&
            AxionToolSelectionController.selectedSubtool() == AxionSubtool.EXTRUDE
    }
}

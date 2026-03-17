package axion.client.tool

import axion.client.AxionClientState
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.AxionSubtool
import axion.common.model.BlockRegion
import axion.common.model.SelectionState
import axion.common.operation.ClearRegionOperation
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos

object EraseToolController {
    private val dispatcher = SymmetryAwareOperationDispatcher()

    fun onEndTick(client: MinecraftClient) {
        if (!isEraseActive() && AxionClientState.eraseToolState !is EraseToolState.Idle) {
            reset()
        }
    }

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        if (!isEraseActive()) {
            return false
        }

        val blockPos = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val nextState = EraseToolState.FirstCornerSet(blockPos)
        AxionClientState.updateEraseToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun handleSecondaryAction(client: MinecraftClient): Boolean {
        if (!isEraseActive()) {
            return false
        }

        val secondCorner = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val firstCorner = when (val state = AxionClientState.eraseToolState) {
            EraseToolState.Idle -> return false
            is EraseToolState.FirstCornerSet -> state.firstCorner
            is EraseToolState.RegionDefined -> state.firstCorner
        }

        val nextState = EraseToolState.RegionDefined(
            firstCorner,
            BlockRegion(firstCorner, secondCorner).normalized(),
        )
        AxionClientState.updateEraseToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun handleMiddleAction(client: MinecraftClient): Boolean {
        if (!isEraseActive()) {
            return false
        }

        val state = AxionClientState.eraseToolState
        if (state !is EraseToolState.RegionDefined) {
            return false
        }

        val expanded = SelectionController.expandRegionToCurrentTarget(client, state.region) ?: return false
        val nextState = EraseToolState.RegionDefined(state.region.remapCorner(state.firstCorner, expanded), expanded)
        AxionClientState.updateEraseToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun handleDeleteAction(client: MinecraftClient): Boolean {
        if (!isEraseActive()) {
            return false
        }

        val state = AxionClientState.eraseToolState
        if (state !is EraseToolState.RegionDefined) {
            return false
        }

        dispatcher.dispatch(ClearRegionOperation(state.region))
        reset()
        return true
    }

    fun reset() {
        val nextState = EraseToolState.Idle
        AxionClientState.updateEraseToolState(nextState)
        syncSelectionState(nextState)
    }

    private fun syncSelectionState(state: EraseToolState) {
        val selectionState = when (state) {
            EraseToolState.Idle -> SelectionState.Idle
            is EraseToolState.FirstCornerSet -> SelectionState.FirstCornerSet(state.firstCorner)
            is EraseToolState.RegionDefined -> SelectionState.RegionDefined(
                state.firstCorner,
                state.region.oppositeCorner(state.firstCorner),
            )
        }

        AxionClientState.updateSelection(selectionState)
    }

    private fun isEraseActive(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive() &&
            AxionToolSelectionController.selectedSubtool() == AxionSubtool.ERASE
    }
}

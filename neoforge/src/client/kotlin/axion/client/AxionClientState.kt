package axion.client

import axion.client.render.MoveSourceRenderState
import axion.common.model.ClipboardState
import axion.common.model.GlobalModeState
import axion.common.model.SelectionState
import axion.common.model.SymmetryState
import axion.common.model.AxionSubtool
import axion.common.model.ToolSelectionState

object AxionClientState {
    var toolSelectionState: ToolSelectionState = ToolSelectionState.Vanilla(slot = 0)
        private set

    var selectionState: SelectionState = SelectionState.Idle
        private set

    var selectedSubtool: AxionSubtool = AxionSubtool.MOVE
        private set

    var placementToolState: axion.client.itemStack.CloneToolState = axion.client.itemStack.CloneToolState.Idle
        private set

    var eraseToolState: axion.client.itemStack.EraseToolState = axion.client.itemStack.EraseToolState.Idle
        private set

    var stackToolState: axion.client.itemStack.StackToolState = axion.client.itemStack.StackToolState.Idle
        private set

    var smearToolState: axion.client.itemStack.SmearToolState = axion.client.itemStack.SmearToolState.Idle
        private set

    var extrudeToolState: axion.client.itemStack.ExtrudeToolState = axion.client.itemStack.ExtrudeToolState.Idle
        private set

    var clipboardState: ClipboardState = ClipboardState.Empty
        private set

    var symmetryState: SymmetryState = SymmetryState.Inactive
        private set

    var symmetryPreviewState: axion.client.symmetry.SymmetryPreviewState? = null
        private set

    var globalModeState: GlobalModeState = GlobalModeState()
        private set

    var middleClickMagicSelectEnabled: Boolean = false
        private set

    var keepExistingEnabled: Boolean = false
        private set

    var copyEntitiesEnabled: Boolean = false
        private set

    var copyAirEnabled: Boolean = false
        private set

    var flySpeedMultiplier: Float = 1.0f
        private set

    fun updateToolSelection(state: ToolSelectionState) {
        toolSelectionState = state
    }

    fun updateSelection(state: SelectionState) {
        selectionState = state
    }

    fun updateSelectedSubtool(subtool: AxionSubtool) {
        selectedSubtool = subtool
    }

    fun updatePlacementToolState(state: axion.client.itemStack.CloneToolState) {
        placementToolState = state
        MoveSourceRenderState.synchronize(state)
    }

    fun updateEraseToolState(state: axion.client.itemStack.EraseToolState) {
        eraseToolState = state
    }

    fun updateStackToolState(state: axion.client.itemStack.StackToolState) {
        stackToolState = state
    }

    fun updateSmearToolState(state: axion.client.itemStack.SmearToolState) {
        smearToolState = state
    }

    fun updateExtrudeToolState(state: axion.client.itemStack.ExtrudeToolState) {
        extrudeToolState = state
    }

    fun updateClipboard(state: ClipboardState) {
        clipboardState = state
    }

    fun updateSymmetry(state: SymmetryState) {
        symmetryState = state
    }

    fun updateSymmetryPreview(state: axion.client.symmetry.SymmetryPreviewState?) {
        symmetryPreviewState = state
    }

    fun updateGlobalModes(state: GlobalModeState) {
        globalModeState = state
    }

    fun updateMiddleClickMagicSelect(enabled: Boolean) {
        middleClickMagicSelectEnabled = enabled
    }

    fun updateKeepExisting(enabled: Boolean) {
        keepExistingEnabled = enabled
    }

    fun updateCopyEntities(enabled: Boolean) {
        copyEntitiesEnabled = enabled
    }

    fun updateCopyAir(enabled: Boolean) {
        copyAirEnabled = enabled
    }

    fun updateFlySpeedMultiplier(multiplier: Float) {
        flySpeedMultiplier = multiplier.coerceIn(1.0f, 9.99f)
    }
}

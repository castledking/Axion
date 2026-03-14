package axion.client

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

    var placementToolState: axion.client.tool.CloneToolState = axion.client.tool.CloneToolState.Idle
        private set

    var eraseToolState: axion.client.tool.EraseToolState = axion.client.tool.EraseToolState.Idle
        private set

    var stackToolState: axion.client.tool.StackToolState = axion.client.tool.StackToolState.Idle
        private set

    var smearToolState: axion.client.tool.SmearToolState = axion.client.tool.SmearToolState.Idle
        private set

    var extrudeToolState: axion.client.tool.ExtrudeToolState = axion.client.tool.ExtrudeToolState.Idle
        private set

    var clipboardState: ClipboardState = ClipboardState.Empty
        private set

    var symmetryState: SymmetryState = SymmetryState.Inactive
        private set

    var symmetryPreviewState: axion.client.symmetry.SymmetryPreviewState? = null
        private set

    var globalModeState: GlobalModeState = GlobalModeState()
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

    fun updatePlacementToolState(state: axion.client.tool.CloneToolState) {
        placementToolState = state
    }

    fun updateEraseToolState(state: axion.client.tool.EraseToolState) {
        eraseToolState = state
    }

    fun updateStackToolState(state: axion.client.tool.StackToolState) {
        stackToolState = state
    }

    fun updateSmearToolState(state: axion.client.tool.SmearToolState) {
        smearToolState = state
    }

    fun updateExtrudeToolState(state: axion.client.tool.ExtrudeToolState) {
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
}

package axion.common.model

sealed interface ToolSelectionState {
    data class Vanilla(val slot: Int) : ToolSelectionState

    data class Axion(val previousVanillaSlot: Int) : ToolSelectionState
}

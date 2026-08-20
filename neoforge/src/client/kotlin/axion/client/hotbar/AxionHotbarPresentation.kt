package axion.client.hotbar

import axion.common.model.ToolSelectionState

/** Presentation-only policy for the vanilla hotbar selection frame. */
object AxionHotbarPresentation {
    const val HIDDEN_SELECTOR_SLOT: Int = -1_000_000

    fun vanillaSelectorSlot(
        originalSlot: Int,
        selectionState: ToolSelectionState,
        creativeAllowed: Boolean,
    ): Int = if (creativeAllowed && selectionState is ToolSelectionState.Axion) {
        HIDDEN_SELECTOR_SLOT
    } else {
        originalSlot
    }
}

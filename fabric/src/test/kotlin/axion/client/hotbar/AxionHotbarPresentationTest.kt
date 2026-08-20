package axion.client.hotbar

import axion.common.model.ToolSelectionState
import kotlin.test.Test
import kotlin.test.assertEquals

class AxionHotbarPresentationTest {
    @Test
    fun axionSelectionHidesVanillaSelectorForEveryEntryPath() {
        listOf(0, 4, 8).forEach { previousSlot ->
            assertEquals(
                AxionHotbarPresentation.HIDDEN_SELECTOR_SLOT,
                AxionHotbarPresentation.vanillaSelectorSlot(
                    originalSlot = previousSlot,
                    selectionState = ToolSelectionState.Axion(previousSlot),
                    creativeAllowed = true,
                ),
            )
        }
    }

    @Test
    fun vanillaSelectionKeepsItsHighlight() {
        assertEquals(
            6,
            AxionHotbarPresentation.vanillaSelectorSlot(
                originalSlot = 6,
                selectionState = ToolSelectionState.Vanilla(6),
                creativeAllowed = true,
            ),
        )
    }

    @Test
    fun nonCreativePlayerKeepsVanillaHighlight() {
        assertEquals(
            2,
            AxionHotbarPresentation.vanillaSelectorSlot(
                originalSlot = 2,
                selectionState = ToolSelectionState.Axion(2),
                creativeAllowed = false,
            ),
        )
    }
}

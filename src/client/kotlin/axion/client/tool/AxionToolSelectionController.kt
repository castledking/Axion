package axion.client.tool

import axion.client.AxionClientState
import axion.common.model.AxionSubtool
import axion.common.model.ClipboardState
import axion.common.model.ToolSelectionState
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.MathHelper

object AxionToolSelectionController {
    private const val HOTBAR_SLOT_COUNT: Int = 9
    private const val AXION_SLOT_INDEX: Int = HOTBAR_SLOT_COUNT
    private const val TOTAL_SCROLL_POSITIONS: Int = HOTBAR_SLOT_COUNT + 1

    fun currentState(): ToolSelectionState = AxionClientState.toolSelectionState

    fun isCreativeModeAllowed(): Boolean = MinecraftClient.getInstance().player?.isInCreativeMode == true

    fun isAxionSlotActive(): Boolean = isCreativeModeAllowed() && currentState() is ToolSelectionState.Axion

    fun isAxionSelected(): Boolean = isAxionSlotActive()

    fun selectedSubtool(): AxionSubtool = AxionClientState.selectedSubtool

    fun selectSubtool(subtool: AxionSubtool) {
        if (!isCreativeModeAllowed() || !isAxionSlotActive()) {
            return
        }

        AxionClientState.updateSelectedSubtool(subtool)
    }

    fun syncWithPlayerSlot(selectedSlot: Int) {
        if (!isCreativeModeAllowed()) {
            if (currentState() is ToolSelectionState.Axion) {
                clearTransientSelection()
                AxionClientState.updateToolSelection(ToolSelectionState.Vanilla(selectedSlot))
            }
            return
        }

        when (val state = currentState()) {
            is ToolSelectionState.Vanilla -> {
                if (state.slot != selectedSlot) {
                    AxionClientState.updateToolSelection(ToolSelectionState.Vanilla(selectedSlot))
                }
            }

            is ToolSelectionState.Axion -> {
                if (state.previousVanillaSlot != selectedSlot) {
                    clearTransientSelection()
                    AxionClientState.updateToolSelection(ToolSelectionState.Vanilla(selectedSlot))
                }
            }
        }
    }

    fun toggleAxionTool(selectedSlot: Int) {
        if (!isCreativeModeAllowed()) {
            return
        }

        when (currentState()) {
            is ToolSelectionState.Axion -> {
                clearTransientSelection()
                AxionClientState.updateToolSelection(ToolSelectionState.Vanilla(selectedSlot))
            }
            is ToolSelectionState.Vanilla -> AxionClientState.updateToolSelection(ToolSelectionState.Axion(selectedSlot))
        }
    }

    fun cycleSubtool(step: Int) {
        if (!isCreativeModeAllowed()) {
            return
        }

        val state = currentState()
        if (state !is ToolSelectionState.Axion || step == 0) {
            return
        }

        AxionClientState.updateSelectedSubtool(AxionClientState.selectedSubtool.cycle(step))
    }

    fun handleHotbarScroll(currentVanillaSlot: Int, scrollAmount: Double, altHeld: Boolean): ScrollOutcome {
        if (!isCreativeModeAllowed()) {
            return ScrollOutcome.PassThrough
        }

        val direction = scrollAmount.compareTo(0.0)
        if (direction == 0) {
            return ScrollOutcome.PassThrough
        }

        if (altHeld && isAxionSelected()) {
            cycleSubtool(step = -direction)
            return ScrollOutcome.Consumed
        }

        val currentScrollIndex = when (val state = currentState()) {
            is ToolSelectionState.Vanilla -> state.slot
            is ToolSelectionState.Axion -> AXION_SLOT_INDEX
        }

        val nextIndex = MathHelper.floorMod(currentScrollIndex - direction, TOTAL_SCROLL_POSITIONS)
        return if (nextIndex == AXION_SLOT_INDEX) {
            AxionClientState.updateToolSelection(ToolSelectionState.Axion(previousVanillaSlot = currentVanillaSlot))
            ScrollOutcome.Consumed
        } else {
            clearTransientSelection()
            AxionClientState.updateToolSelection(ToolSelectionState.Vanilla(slot = nextIndex))
            ScrollOutcome.SelectVanilla(nextIndex)
        }
    }

    private fun clearTransientSelection() {
        AxionClientState.updateClipboard(ClipboardState.Empty)
    }

    sealed interface ScrollOutcome {
        data object PassThrough : ScrollOutcome
        data object Consumed : ScrollOutcome
        data class SelectVanilla(val slot: Int) : ScrollOutcome
    }
}

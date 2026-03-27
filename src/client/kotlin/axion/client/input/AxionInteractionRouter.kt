package axion.client.input

import axion.client.AxionClientState
import axion.client.config.MagicSelectMaskConfigScreen
import axion.client.hotbar.SavedHotbarController
import axion.client.symmetry.SymmetryController
import axion.client.symmetry.SymmetryPlacementController
import axion.client.tool.AxionToolSelectionController
import axion.client.tool.CloneToolState
import axion.client.tool.EraseToolController
import axion.client.tool.EraseToolState
import axion.client.tool.ExtrudeToolController
import axion.client.tool.MagicSelectionService
import axion.client.tool.PlacementToolController
import axion.client.tool.SmearToolState
import axion.client.tool.SmearToolController
import axion.client.tool.StackToolState
import axion.client.tool.StackToolController
import axion.common.model.AxionSubtool
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

object AxionInteractionRouter {
    private var suppressPrimaryUntilRelease: Boolean = false
    private var suppressSecondaryUntilRelease: Boolean = false

    fun onEndTick(client: MinecraftClient) {
        if (!client.options.attackKey.isPressed) {
            suppressPrimaryUntilRelease = false
        } else if (suppressPrimaryUntilRelease) {
            client.interactionManager?.cancelBlockBreaking()
        }

        if (!client.options.useKey.isPressed) {
            suppressSecondaryUntilRelease = false
        }
    }

    fun shouldSuppressPrimary(client: MinecraftClient): Boolean {
        if (!suppressPrimaryUntilRelease) {
            return false
        }

        if (!client.options.attackKey.isPressed) {
            suppressPrimaryUntilRelease = false
            return false
        }

        client.interactionManager?.cancelBlockBreaking()
        return true
    }

    fun shouldSuppressSecondary(client: MinecraftClient): Boolean {
        if (!suppressSecondaryUntilRelease) {
            return false
        }

        if (!client.options.useKey.isPressed) {
            suppressSecondaryUntilRelease = false
            return false
        }

        return true
    }

    fun ownsPrimaryAction(): Boolean {
        return shouldCapturePrimaryAction()
    }

    fun consumePrimaryAction(client: MinecraftClient): Boolean {
        val handled = handlePrimaryAction(client)
        if (!handled && !shouldCapturePrimaryAction()) {
            return false
        }

        suppressPrimaryUntilRelease = true
        client.interactionManager?.cancelBlockBreaking()
        return true
    }

    fun consumeSecondaryAction(client: MinecraftClient): Boolean {
        val handled = handleSecondaryAction(client)
        if (!handled && !shouldCaptureSecondaryAction()) {
            return false
        }

        suppressSecondaryUntilRelease = true
        client.player?.stopUsingItem()
        return true
    }

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.CLONE,
            AxionSubtool.MOVE,
                -> PlacementToolController.handlePrimaryAction(client)
            AxionSubtool.STACK -> StackToolController.handlePrimaryAction(client)
            AxionSubtool.SMEAR -> SmearToolController.handlePrimaryAction(client)
            AxionSubtool.EXTRUDE -> ExtrudeToolController.handlePrimaryAction(client)
            AxionSubtool.SETUP_SYMMETRY -> SymmetryController.handlePrimaryAction(client)
            AxionSubtool.ERASE -> EraseToolController.handlePrimaryAction(client)
        }
    }

    fun handleSecondaryAction(client: MinecraftClient): Boolean {
        if (SymmetryPlacementController.handleUse(client)) {
            suppressSecondaryUntilRelease = true
            return true
        }

        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.CLONE,
            AxionSubtool.MOVE,
                -> PlacementToolController.handleSecondaryAction(client)
            AxionSubtool.STACK -> StackToolController.handleSecondaryAction(client)
            AxionSubtool.SMEAR -> SmearToolController.handleSecondaryAction(client)
            AxionSubtool.EXTRUDE -> ExtrudeToolController.handleSecondaryAction(client)
            AxionSubtool.SETUP_SYMMETRY -> SymmetryController.handleSecondaryAction(client)
            AxionSubtool.ERASE -> EraseToolController.handleSecondaryAction(client)
        }
    }

    fun handleMiddleAction(client: MinecraftClient): Boolean {
        if (AxionModifierKeys.isControlDown(client) && supportsMagicSelectConfigShortcut()) {
            client.setScreen(MagicSelectMaskConfigScreen(client.currentScreen))
            return true
        }

        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.CLONE,
            AxionSubtool.MOVE,
                -> PlacementToolController.handleMiddleAction(client)
            AxionSubtool.STACK -> StackToolController.handleMiddleAction(client)
            AxionSubtool.SMEAR -> SmearToolController.handleMiddleAction(client)
            AxionSubtool.ERASE -> EraseToolController.handleMiddleAction(client)
            else -> false
        }
    }

    fun handleDeleteAction(client: MinecraftClient): Boolean {
        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.ERASE -> EraseToolController.handleDeleteAction(client)
            AxionSubtool.SETUP_SYMMETRY -> SymmetryController.handleDeleteAction(client)
            else -> false
        }
    }

    fun handleScroll(
        client: MinecraftClient,
        currentVanillaSlot: Int,
        scrollAmount: Double,
        altHeld: Boolean,
        ctrlHeld: Boolean,
    ): AxionToolSelectionController.ScrollOutcome {
        if (altHeld && !AxionToolSelectionController.isAxionSlotActive()) {
            if (SavedHotbarController.handleScroll(client, scrollAmount)) {
                return AxionToolSelectionController.ScrollOutcome.Consumed
            }
        }

        if (ctrlHeld && AxionToolSelectionController.isAxionSlotActive()) {
            if (handleMagicSelectBrushScroll(client, scrollAmount)) {
                return AxionToolSelectionController.ScrollOutcome.Consumed
            }
            when (AxionToolSelectionController.selectedSubtool()) {
                AxionSubtool.SETUP_SYMMETRY -> {
                    if (SymmetryController.handleScroll(client, scrollAmount)) {
                        return AxionToolSelectionController.ScrollOutcome.Consumed
                    }
                }

                else -> Unit
            }
        }

        if (!altHeld && AxionToolSelectionController.isAxionSlotActive()) {
            when (AxionToolSelectionController.selectedSubtool()) {
                AxionSubtool.CLONE,
                AxionSubtool.MOVE,
                    -> {
                    if (PlacementToolController.handleScroll(client, scrollAmount)) {
                        return AxionToolSelectionController.ScrollOutcome.Consumed
                    }
                }

                AxionSubtool.STACK -> {
                    if (StackToolController.handleScroll(client, scrollAmount)) {
                        return AxionToolSelectionController.ScrollOutcome.Consumed
                    }
                }

                AxionSubtool.SMEAR -> {
                    if (SmearToolController.handleScroll(client, scrollAmount)) {
                        return AxionToolSelectionController.ScrollOutcome.Consumed
                    }
                }

                else -> Unit
            }
        }

        return AxionToolSelectionController.handleHotbarScroll(
            currentVanillaSlot = currentVanillaSlot,
            scrollAmount = scrollAmount,
            altHeld = altHeld,
        )
    }

    private fun shouldCapturePrimaryAction(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive()
    }

    private fun shouldCaptureSecondaryAction(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive()
    }

    private fun supportsMagicSelectConfigShortcut(): Boolean {
        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.CLONE,
            AxionSubtool.MOVE,
            AxionSubtool.STACK,
            AxionSubtool.SMEAR,
            AxionSubtool.ERASE,
                -> AxionToolSelectionController.isAxionSlotActive()

            else -> false
        }
    }

    private fun handleMagicSelectBrushScroll(client: MinecraftClient, scrollAmount: Double): Boolean {
        if (!AxionClientState.middleClickMagicSelectEnabled || !supportsMagicSelectBrushScroll()) {
            return false
        }
        val nextBrushSize = MagicSelectionService.adjustBrushSize(scrollAmount) ?: return false
        client.inGameHud.setOverlayMessage(Text.literal("Axion Magic Select Brush Size: $nextBrushSize"), false)
        return true
    }

    private fun supportsMagicSelectBrushScroll(): Boolean {
        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.CLONE,
            AxionSubtool.MOVE,
                -> when (AxionClientState.placementToolState) {
                    CloneToolState.Idle,
                    is CloneToolState.FirstCornerSet,
                    is CloneToolState.RegionDefined,
                        -> true

                    is CloneToolState.PreviewingOffset,
                    is CloneToolState.AwaitingConfirm,
                        -> false
                }

            AxionSubtool.STACK -> when (AxionClientState.stackToolState) {
                StackToolState.Idle,
                is StackToolState.FirstCornerSet,
                is StackToolState.RegionDefined,
                    -> true

                is StackToolState.PreviewingStack -> false
            }

            AxionSubtool.SMEAR -> when (AxionClientState.smearToolState) {
                SmearToolState.Idle,
                is SmearToolState.FirstCornerSet,
                is SmearToolState.RegionDefined,
                    -> true

                is SmearToolState.PreviewingSmear -> false
            }

            AxionSubtool.ERASE -> when (AxionClientState.eraseToolState) {
                EraseToolState.Idle,
                is EraseToolState.FirstCornerSet,
                is EraseToolState.RegionDefined,
                    -> true
            }

            else -> false
        }
    }
}

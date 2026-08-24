package axion.client.input

import axion.client.AxionClientState
import axion.client.config.MagicSelectMaskConfigScreen
import axion.client.hotbar.SavedHotbarController
import axion.client.symmetry.SymmetryController
import axion.client.symmetry.SymmetryPlacementController
import axion.client.itemStack.AxionToolSelectionController
import axion.client.itemStack.CloneToolState
import axion.client.itemStack.EraseBrushSize
import axion.client.itemStack.EraseToolController
import axion.client.itemStack.EraseToolState
import axion.client.itemStack.ExtrudeToolController
import axion.client.itemStack.MagicSelectionService
import axion.client.itemStack.PlacementToolController
import axion.client.itemStack.RegionEraseService
import axion.client.itemStack.SmearToolState
import axion.client.itemStack.SmearToolController
import axion.client.itemStack.StackToolState
import axion.client.itemStack.StackToolController
import axion.common.model.AxionSubtool
import axion.common.model.ClipboardState
import axion.client.current.SelectionController
import axion.client.compat.toImmutable
import axion.client.current.blockPosOrNull
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import org.lwjgl.glfw.GLFW

object AxionInteractionRouter {
    private var suppressPrimaryUntilRelease: Boolean = false
    private var suppressSecondaryUntilRelease: Boolean = false
    private var lastHeldMiddleMagicTarget: BlockPos? = null

    fun onEndTick(client: Minecraft) {
        if (!client.options.keyAttack.isDown) {
            suppressPrimaryUntilRelease = false
        } else if (suppressPrimaryUntilRelease) {
            client.gameMode?.stopDestroyBlock()
        }

        if (!client.options.keyUse.isDown) {
            suppressSecondaryUntilRelease = false
        }

        handleHeldMiddleMagicSelect(client)
    }

    fun shouldSuppressPrimary(client: Minecraft): Boolean {
        if (!suppressPrimaryUntilRelease) {
            return false
        }

        if (!client.options.keyAttack.isDown) {
            suppressPrimaryUntilRelease = false
            return false
        }

        client.gameMode?.stopDestroyBlock()
        return true
    }

    fun shouldSuppressSecondary(client: Minecraft): Boolean {
        if (!suppressSecondaryUntilRelease) {
            return false
        }

        if (!client.options.keyUse.isDown) {
            suppressSecondaryUntilRelease = false
            return false
        }

        return true
    }

    fun ownsPrimaryAction(): Boolean {
        return shouldCapturePrimaryAction()
    }

    fun consumePrimaryAction(client: Minecraft): Boolean {
        val handled = handlePrimaryAction(client)
        if (!handled && !shouldCapturePrimaryAction()) {
            return false
        }

        suppressPrimaryUntilRelease = true
        client.gameMode?.stopDestroyBlock()
        return true
    }

    fun consumeSecondaryAction(client: Minecraft): Boolean {
        val handled = handleSecondaryAction(client)
        if (!handled && !shouldCaptureSecondaryAction()) {
            return false
        }

        suppressSecondaryUntilRelease = true
        client.player?.releaseUsingItem()
        return true
    }

    fun handlePrimaryAction(client: Minecraft): Boolean {
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

    fun handleSecondaryAction(client: Minecraft): Boolean {
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

    fun handleMiddleAction(client: Minecraft): Boolean {
        if (AxionModifierKeys.isControlDown(client) && supportsMagicSelectConfigShortcut()) {
            client.setScreen(MagicSelectMaskConfigScreen(client.screen))
            return true
        }

        val handled = when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.CLONE,
            AxionSubtool.MOVE,
                -> PlacementToolController.handleMiddleAction(client)
            AxionSubtool.STACK -> StackToolController.handleMiddleAction(client)
            AxionSubtool.SMEAR -> SmearToolController.handleMiddleAction(client)
            AxionSubtool.ERASE -> EraseToolController.handleMiddleAction(client)
            else -> false
        }
        if (handled && supportsHeldMiddleMagicSelect()) {
            lastHeldMiddleMagicTarget = currentTargetBlock()
        }
        return handled
    }

    fun handleDeleteAction(client: Minecraft): Boolean {
        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.ERASE -> EraseToolController.handleDeleteAction(client)
            AxionSubtool.SETUP_SYMMETRY -> SymmetryController.handleDeleteAction(client)

            // Undocumented convenience: once a tool has both corners set, delete
            // clears that region in place instead of forcing a trip through the
            // erase tool. Still a normal dispatch, so undo restores it.
            AxionSubtool.CLONE,
            AxionSubtool.MOVE,
                -> eraseDefinedRegion(
                    (AxionClientState.placementToolState as? CloneToolState.RegionDefined)
                        ?.let { RegionEraseService.Target(it.region, it.clipboardScratchBuffer) }
                        ?: magicSelectionTarget(),
                    PlacementToolController::reset,
                )

            AxionSubtool.STACK -> eraseDefinedRegion(
                (AxionClientState.stackToolState as? StackToolState.RegionDefined)
                    ?.let { RegionEraseService.Target(it.region, it.clipboardScratchBuffer) }
                    ?: magicSelectionTarget(),
                StackToolController::reset,
            )

            AxionSubtool.SMEAR -> eraseDefinedRegion(
                (AxionClientState.smearToolState as? SmearToolState.RegionDefined)
                    ?.let { RegionEraseService.Target(it.region, it.clipboardScratchBuffer) }
                    ?: magicSelectionTarget(),
                SmearToolController::reset,
            )

            AxionSubtool.EXTRUDE -> false
        }
    }

    /**
     * Magic Select stores its region on the clipboard rather than in the tool's
     * own state, so Stack/Clone/Smear/Move never reach [CloneToolState.RegionDefined]
     * and friends when the region came from a magic selection. Without this the
     * delete key silently does nothing for exactly the selections that are
     * quickest to make. Erase already works this way.
     */
    private fun magicSelectionTarget(): RegionEraseService.Target? {
        val magic = AxionClientState.clipboardState as? ClipboardState.MagicSelection ?: return null
        return RegionEraseService.Target(magic.region, magic.clipboardScratchBuffer)
    }

    private fun eraseDefinedRegion(target: RegionEraseService.Target?, reset: () -> Unit): Boolean {
        if (target == null || !AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        RegionEraseService.erase(target.region, target.clipboardManager)
        reset()
        return true
    }

    fun handleScroll(
        client: Minecraft,
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
            // With no corner set, right click means the connected erase, so the
            // scroll sizes that brush rather than the magic-select one.
            if (AxionToolSelectionController.selectedSubtool() == AxionSubtool.ERASE &&
                AxionClientState.eraseToolState == EraseToolState.Idle
            ) {
                // Consume even at the clamp, so scrolling past the limit does not
                // silently fall through and resize the magic-select brush instead.
                handleEraseBrushScroll(client, scrollAmount)
                return AxionToolSelectionController.ScrollOutcome.Consumed
            }
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

        if (!altHeld && !AxionToolSelectionController.isAxionSlotActive()) {
            SavedHotbarController.flushActiveHotbar(client)
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

    private fun handleHeldMiddleMagicSelect(client: Minecraft) {
        if (client.screen != null || !isMiddleMousePressed(client)) {
            lastHeldMiddleMagicTarget = null
            return
        }

        if (AxionModifierKeys.isControlDown(client) || !supportsHeldMiddleMagicSelect()) {
            lastHeldMiddleMagicTarget = currentTargetBlock()
            return
        }

        val target = currentTargetBlock()
        if (target == null) {
            lastHeldMiddleMagicTarget = null
            return
        }
        if (target == lastHeldMiddleMagicTarget) {
            return
        }

        lastHeldMiddleMagicTarget = target
        handleMiddleAction(client)
    }

    private fun isMiddleMousePressed(client: Minecraft): Boolean {
        return GLFW.glfwGetMouseButton(client.window.handle, GLFW.MOUSE_BUTTON_MIDDLE) == GLFW.PRESS
    }

    private fun currentTargetBlock(): BlockPos? {
        return SelectionController.currentTarget().blockPosOrNull()?.toImmutable()
    }

    private fun supportsHeldMiddleMagicSelect(): Boolean {
        if (!AxionClientState.middleClickMagicSelectEnabled || !AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }

        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.CLONE,
            AxionSubtool.MOVE,
                -> when (AxionClientState.placementToolState) {
                    CloneToolState.Idle,
                    is CloneToolState.FirstCornerSet,
                        -> true

                    is CloneToolState.RegionDefined,
                    is CloneToolState.PreviewingOffset,
                    is CloneToolState.AwaitingConfirm,
                        -> false
                }

            AxionSubtool.STACK -> when (AxionClientState.stackToolState) {
                StackToolState.Idle,
                is StackToolState.FirstCornerSet,
                    -> true

                is StackToolState.RegionDefined,
                is StackToolState.PreviewingStack,
                    -> false
            }

            AxionSubtool.SMEAR -> when (AxionClientState.smearToolState) {
                SmearToolState.Idle,
                is SmearToolState.FirstCornerSet,
                    -> true

                is SmearToolState.RegionDefined,
                is SmearToolState.PreviewingSmear,
                    -> false
            }

            AxionSubtool.ERASE -> when (AxionClientState.eraseToolState) {
                EraseToolState.Idle,
                is EraseToolState.FirstCornerSet,
                    -> true

                is EraseToolState.RegionDefined,
                    -> false
            }

            else -> false
        }
    }

    private fun handleEraseBrushScroll(client: Minecraft, scrollAmount: Double): Boolean {
        val nextBrushSize = EraseBrushSize.adjust(scrollAmount) ?: return false
        client.gui.setOverlayMessage(Component.literal("Axion Erase Brush Size: $nextBrushSize"), false)
        return true
    }

    private fun handleMagicSelectBrushScroll(client: Minecraft, scrollAmount: Double): Boolean {
        if (!AxionClientState.middleClickMagicSelectEnabled || !supportsMagicSelectBrushScroll()) {
            return false
        }
        val nextBrushSize = MagicSelectionService.adjustBrushSize(scrollAmount) ?: return false
        client.gui.setOverlayMessage(Component.literal("Axion Magic Select Brush Size: $nextBrushSize"), false)
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

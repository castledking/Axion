package axion.client.input

import axion.client.AxionClientState
import axion.client.config.MagicSelectMaskConfigScreen
import axion.client.hotbar.SavedHotbarController
import axion.client.symmetry.SymmetryController
import axion.client.symmetry.SymmetryPlacementController
import axion.client.tool.AxionToolSelectionController
import axion.client.tool.CloneToolState
import axion.client.tool.EraseBrushSize
import axion.client.tool.EraseToolController
import axion.client.tool.EraseToolState
import axion.client.tool.ExtrudeToolController
import axion.client.tool.MagicSelectionService
import axion.client.tool.PlacementToolController
import axion.client.tool.RegionEraseService
import axion.client.tool.SmearToolState
import axion.client.tool.SmearToolController
import axion.client.tool.StackToolState
import axion.client.tool.StackToolController
import axion.common.model.AxionSubtool
import axion.common.model.ClipboardState
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
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
                        ?.let { RegionEraseService.Target(it.region, it.clipboardBuffer) }
                        ?: magicSelectionTarget(),
                    PlacementToolController::reset,
                )

            AxionSubtool.STACK -> eraseDefinedRegion(
                (AxionClientState.stackToolState as? StackToolState.RegionDefined)
                    ?.let { RegionEraseService.Target(it.region, it.clipboardBuffer) }
                    ?: magicSelectionTarget(),
                StackToolController::reset,
            )

            AxionSubtool.SMEAR -> eraseDefinedRegion(
                (AxionClientState.smearToolState as? SmearToolState.RegionDefined)
                    ?.let { RegionEraseService.Target(it.region, it.clipboardBuffer) }
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
        return RegionEraseService.Target(magic.region, magic.clipboardBuffer)
    }

    private fun eraseDefinedRegion(target: RegionEraseService.Target?, reset: () -> Unit): Boolean {
        if (target == null || !AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        RegionEraseService.erase(target.region, target.clipboard)
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
        return GLFW.glfwGetMouseButton(axion.client.compat.VersionCompatImpl.glfwWindowHandle(client), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS
    }

    private fun currentTargetBlock(): BlockPos? {
        return SelectionController.currentTarget().blockPosOrNull()
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

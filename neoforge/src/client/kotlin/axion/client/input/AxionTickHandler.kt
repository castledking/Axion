package axion.client.input

import axion.client.compat.CrowBarCompat
import axion.client.compat.VersionCompatImpl
import axion.client.config.AxionClientConfig
import axion.client.config.AxionConfigScreen
import axion.client.hotbar.AxionAltMenuController
import axion.client.hotbar.SavedHotbarController
import axion.client.hotbar.SavedHotbarGameModeController
import axion.client.history.UndoRedoController
import axion.client.mode.ClientModeController
import axion.client.network.AxionServerConnection
import axion.client.selection.SelectionController
import axion.client.symmetry.SymmetryController
import axion.client.tool.AxionToolSelectionController
import axion.client.tool.EraseToolController
import axion.client.tool.ExtrudeToolController
import axion.client.tool.PlacementToolController
import axion.client.tool.SmearToolController
import axion.client.tool.StackToolController
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel

object AxionTickHandler {
    // Tracks the world identity to detect dimension changes / world unloads.
    // When the world reference flips (including to null), every chunked
    // preview session is closed so GPU/CPU caches don't leak into the next
    // world with stale absolute-coord chunks.
    private var lastObservedWorld: ClientLevel? = null
    private var dispatchLogs = 0

    fun onEndTick(client: Minecraft) {
        observeWorldLifecycle(client.level)
        AxionServerConnection.onEndTick()
        SelectionController.onEndTick(client)
        AxionInteractionRouter.onEndTick(client)
        val player = client.player
        if (player == null) {
            SavedHotbarGameModeController.reset()
            CrowBarCompat.setLocatorBarSuppressed(false)
            return
        }
        AxionToolSelectionController.syncWithPlayerSlot(player.inventory.selectedSlot)
        SavedHotbarController.onEndTick(client)
        ClientModeController.enforceCreativeMode(client)
        AxionAltMenuController.onEndTick(client)
        if (client.screen == null && !AxionAltMenuController.isActive(client)) {
            ClientModeController.handleToggleKeypresses(client)

            while (AxionKeybindings.selectAxionTool.consumeClick()) {
                if (!AxionToolSelectionController.isAxionSlotActive()) {
                    SavedHotbarController.flushActiveHotbar(client)
                }
                AxionToolSelectionController.toggleAxionTool(player.inventory.selectedSlot)
            }

            while (AxionKeybindings.nextSubtool.consumeClick()) {
                AxionToolSelectionController.cycleSubtool(step = 1)
            }

            while (AxionKeybindings.previousSubtool.consumeClick()) {
                AxionToolSelectionController.cycleSubtool(step = -1)
            }

            while (AxionKeybindings.toolDeleteAction.consumeClick()) {
                AxionInteractionRouter.handleDeleteAction(client)
            }

            // Use wasCtrlComboPressed for modifier combos (Ctrl+R, Ctrl+F) because MC 1.21.8+
            // suppresses KeyBinding.isDown when modifier keys are held, and consumeClick()
            // can be consumed by conflicting vanilla bindings. wasCtrlComboPressed reads both
            // keys directly from GLFW and edge-detects the combo, bypassing both issues.
            if (KeyBindingHandler.wasCtrlComboPressed(AxionKeybindings.symmetryToggleRotation, allowShift = false)) {
                val handled = PlacementToolController.handleRotateAction()
                if (dispatchLogs < 12) {
                    dispatchLogs++
                    org.slf4j.LoggerFactory.getLogger(AxionTickHandler::class.java).info(
                        "[Axion input] rotate combo → placementToolHandled={} placementState={}",
                        handled, axion.client.AxionClientState.placementToolState::class.simpleName,
                    )
                }
                if (!handled) {
                    SymmetryController.toggleRotational()
                }
            }

            if (KeyBindingHandler.wasCtrlComboPressed(AxionKeybindings.symmetryToggleMirror, allowShift = false)) {
                if (!PlacementToolController.handleMirrorAction(client)) {
                    SymmetryController.toggleMirror(client)
                }
            }

            if (KeyBindingHandler.wasCtrlComboPressed(AxionKeybindings.symmetryToggleConstruct)) {
                SymmetryController.toggleConstruct()
            }

            if (KeyBindingHandler.wasCtrlComboPressed(AxionKeybindings.undoAction)) {
                UndoRedoController.undo(client)
            }

            if (KeyBindingHandler.wasCtrlComboPressed(AxionKeybindings.redoAction, allowShift = true)) {
                UndoRedoController.redo(client)
            }

            while (AxionKeybindings.openConfigScreen.consumeClick()) {
                client.setScreen(AxionConfigScreen(client.screen))
            }

            while (AxionKeybindings.toggleSameBlockMagicSelect.consumeClick()) {
                val enabled = AxionClientConfig.toggleSameBlockMagicSelect()
                client.gui.setOverlayMessage(
                    net.minecraft.network.chat.Component.translatable(
                        "axion.config.magic_select.same_block_select.overlay",
                        net.minecraft.network.chat.Component.translatable(
                            if (enabled) {
                                "axion.config.toggle.on"
                            } else {
                                "axion.config.toggle.off"
                            },
                        ),
                    ),
                    false,
                )
            }
        }

        SymmetryController.onEndTick(client)
        ClientModeController.onEndTick(client)
        PlacementToolController.onEndTick(client)
        EraseToolController.onEndTick(client)
        StackToolController.onEndTick(client)
        SmearToolController.onEndTick(client)
        ExtrudeToolController.onEndTick(client)
    }

    private fun observeWorldLifecycle(currentWorld: ClientLevel?) {
        if (currentWorld === lastObservedWorld) return
        // World changed (joined a server, switched dimension, returned to title).
        // Free any cached preview chunks — their absolute coordinates are no
        // longer meaningful, and keeping them risks rendering ghost geometry
        // against the wrong world.
        if (lastObservedWorld != null) {
            VersionCompatImpl.closeChunkedPreviews()
        }
        lastObservedWorld = currentWorld
    }
}

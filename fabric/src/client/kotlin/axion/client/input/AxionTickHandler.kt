package axion.client.input

import axion.client.compat.CrowBarCompat
import axion.client.compat.VersionCompatImpl
import axion.client.config.AxionClientConfig
import axion.client.config.AxionConfigScreen
import axion.client.editor.AxionEditorMode
import axion.client.editor.AxionEditorUiBridge
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
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.world.ClientWorld

object AxionTickHandler {
    // Tracks the world identity to detect dimension changes / world unloads.
    // When the world reference flips (including to null), every chunked
    // preview session is closed so GPU/CPU caches don't leak into the next
    // world with stale absolute-coord chunks.
    private var lastObservedWorld: ClientWorld? = null

    fun onStartTick(client: MinecraftClient) {
        // Runs before the player tick so the editor's velocity override is
        // what vanilla's travel integrates this tick.
        AxionEditorMode.onStartTick(client)
    }

    /**
     * Right Shift opens the Axiom-style editor; Open Config is unbound by
     * default so the two never fight over it.
     *
     * Before the editor existed Open Config defaulted to Right Shift, and
     * Minecraft saves every binding to options.txt, so upgraded installs keep
     * both on the same key. How that goes wrong depends on the version: up to
     * 1.21.8 a key feeds exactly one binding, and here it was Open Config, so
     * the editor never opened; from 1.21.9 (and on 26.x) both receive the
     * press, and the config press the editor branch "skipped" stayed queued and
     * opened the config screen a tick later. When both share a key, the editor
     * wins: every queued press is drained each tick, and a press that reached
     * either binding counts once, as an editor toggle.
     */
    private fun handleEditorAndConfigKeys(client: MinecraftClient) {
        val editorPresses = drainPresses(AxionKeybindings.toggleEditorUi)
        val configPresses = drainPresses(AxionKeybindings.openConfigScreen)
        val sharedKey = !AxionKeybindings.toggleEditorUi.isUnbound &&
            AxionKeybindings.toggleEditorUi.boundKeyTranslationKey ==
            AxionKeybindings.openConfigScreen.boundKeyTranslationKey
        // One physical press reaches both bindings on 1.21.9+ and only one of
        // them before that, so presses on a shared key are counted once.
        val editorToggles = if (sharedKey) maxOf(editorPresses, configPresses) else editorPresses
        val configOpens = if (sharedKey) 0 else configPresses

        repeat(editorToggles) {
            if (AxionEditorUiBridge.isAvailable()) {
                AxionEditorMode.toggle(client)
            } else {
                // owo-lib is optional. Without it there is no editor, and the
                // editor key falls back to what Right Shift did before the editor
                // existed: opening the config screen.
                client.setScreen(AxionConfigScreen(client.currentScreen as? Screen))
            }
        }
        repeat(configOpens) {
            client.setScreen(AxionConfigScreen(client.currentScreen as? Screen))
        }
    }

    private fun drainPresses(binding: net.minecraft.client.option.KeyBinding): Int {
        var presses = 0
        while (binding.wasPressed()) {
            presses++
        }
        return presses
    }

    fun onEndTick(client: MinecraftClient) {
        observeWorldLifecycle(client.world)
        AxionEditorMode.onEndTick(client)
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
        if (client.currentScreen == null && !AxionAltMenuController.isActive(client)) {
            ClientModeController.handleToggleKeypresses(client)

            while (AxionKeybindings.selectAxionTool.wasPressed()) {
                if (!AxionToolSelectionController.isAxionSlotActive()) {
                    SavedHotbarController.flushActiveHotbar(client)
                }
                AxionToolSelectionController.toggleAxionTool(player.inventory.selectedSlot)
            }

            while (AxionKeybindings.nextSubtool.wasPressed()) {
                AxionToolSelectionController.cycleSubtool(step = 1)
            }

            while (AxionKeybindings.previousSubtool.wasPressed()) {
                AxionToolSelectionController.cycleSubtool(step = -1)
            }

            while (AxionKeybindings.toolDeleteAction.wasPressed()) {
                AxionInteractionRouter.handleDeleteAction(client)
            }

            // Use wasCtrlComboPressed for modifier combos (Ctrl+R, Ctrl+F) because MC 1.21.8+
            // suppresses KeyBinding.isPressed when modifier keys are held, and wasPressed()
            // can be consumed by conflicting vanilla bindings. wasCtrlComboPressed reads both
            // keys directly from GLFW and edge-detects the combo, bypassing both issues.
            if (KeyBindingHandler.wasCtrlComboPressed(AxionKeybindings.symmetryToggleRotation, allowShift = false)) {
                if (!PlacementToolController.handleRotateAction()) {
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

            handleEditorAndConfigKeys(client)

            while (AxionKeybindings.toggleSameBlockMagicSelect.wasPressed()) {
                val enabled = AxionClientConfig.toggleSameBlockMagicSelect()
                client.inGameHud.setOverlayMessage(
                    net.minecraft.text.Text.translatable(
                        "axion.config.magic_select.same_block_select.overlay",
                        net.minecraft.text.Text.translatable(
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

    private fun observeWorldLifecycle(currentWorld: ClientWorld?) {
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

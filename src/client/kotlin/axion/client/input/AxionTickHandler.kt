package axion.client.input

import axion.client.config.AxionConfigScreen
import axion.client.hotbar.AxionAltMenuController
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

object AxionTickHandler {
    fun onEndTick(client: MinecraftClient) {
        AxionServerConnection.onEndTick()
        AxionInteractionRouter.onEndTick(client)
        val player = client.player ?: return
        AxionToolSelectionController.syncWithPlayerSlot(player.inventory.selectedSlot)
        ClientModeController.enforceCreativeMode(client)
        AxionAltMenuController.onEndTick(client)
        if (client.currentScreen == null && !AxionAltMenuController.isActive(client)) {
            ClientModeController.handleToggleKeypresses(client)

            while (AxionKeybindings.selectAxionTool.wasPressed()) {
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

            while (AxionKeybindings.symmetryToggleRotation.wasPressed()) {
                if (AxionModifierKeys.isControlDown(client)) {
                    if (!PlacementToolController.handleRotateAction()) {
                        SymmetryController.toggleRotational()
                    }
                }
            }

            while (AxionKeybindings.symmetryToggleMirror.wasPressed()) {
                if (AxionModifierKeys.isControlDown(client)) {
                    if (!PlacementToolController.handleMirrorAction(client)) {
                        SymmetryController.toggleMirror(client)
                    }
                }
            }

            while (AxionKeybindings.undoAction.wasPressed()) {
                if (AxionModifierKeys.isControlDown(client)) {
                    if (AxionModifierKeys.isShiftDown(client)) {
                        UndoRedoController.redo(client)
                    } else {
                        UndoRedoController.undo(client)
                    }
                }
            }

            while (AxionKeybindings.redoAction.wasPressed()) {
                if (AxionModifierKeys.isControlDown(client)) {
                    UndoRedoController.redo(client)
                }
            }

            while (AxionKeybindings.openConfigScreen.wasPressed()) {
                client.setScreen(AxionConfigScreen(client.currentScreen))
            }
        }

        SelectionController.onEndTick(client)
        SymmetryController.onEndTick(client)
        ClientModeController.onEndTick(client)
        PlacementToolController.onEndTick(client)
        EraseToolController.onEndTick(client)
        StackToolController.onEndTick(client)
        SmearToolController.onEndTick(client)
        ExtrudeToolController.onEndTick(client)
    }
}

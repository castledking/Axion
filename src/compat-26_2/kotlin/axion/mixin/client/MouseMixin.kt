package axion.mixin.client

import axion.client.compat.LitematicaCompat
import axion.client.editor.AxionEditorMode
import axion.client.editor.ui.AxionEditorUi
import axion.client.hotbar.AxionAltMenuController
import axion.client.input.AxionInteractionRouter
import axion.client.input.AxionModifierKeys
import axion.client.mode.ClientModeController
import axion.client.tool.AxionToolSelectionController
import net.minecraft.client.MinecraftClient
import net.minecraft.client.MouseHandler
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * 26.2.x-specific MouseMixin for the official MouseHandler signatures.
 */
@Mixin(MouseHandler::class)
abstract class MouseMixin {
    private fun getClient(): MinecraftClient = MinecraftClient.getInstance()

    @Inject(
        method = ["onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V"],
        at = [At("HEAD")],
        cancellable = true,
    )
    private fun axionHandleMouseButton(window: Long, mouseButtonInfo: MouseButtonInfo, action: Int, ci: CallbackInfo) {
        val client = getClient()
        val button = mouseButtonInfo.button()
        if (AxionEditorMode.onMouseButton(client, button, action)) {
            ci.cancel()
            return
        }

        if (AxionAltMenuController.handleMouseButton(client, button, action)) {
            ci.cancel()
            return
        }

        if (client.gui.screen() != null || action != GLFW.GLFW_PRESS) {
            return
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT &&
            ClientModeController.shouldLetVanillaHandleSecondaryAction(client)) {
            return
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT &&
            ClientModeController.isFastPlaceEnabled(client)) {
            return
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
            ClientModeController.shouldLetVanillaHandlePrimaryAction(client)) {
            return
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
            ClientModeController.shouldLetVanillaHandleBulldozerInfiniteReach(client)) {
            return
        }

        val consumed = when (button) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> ClientModeController.consumePrimaryAction(client)
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> ClientModeController.consumeSecondaryAction(client)
            else -> false
        }
        if (consumed) {
            ci.cancel()
        }
    }

    // Axiom-style editor: keep the OS cursor free while the editor owns
    // input, and let the right-click camera drag own vanilla's grab instead.
    @Inject(method = ["grabMouse"], at = [At("HEAD")], cancellable = true)
    private fun axionEditorBlockCursorLock(ci: CallbackInfo) {
        if (AxionEditorMode.shouldBlockCursorLock()) {
            ci.cancel()
        }
    }

    @Inject(method = ["releaseMouse"], at = [At("HEAD")], cancellable = true)
    private fun axionEditorBlockCursorUnlock(ci: CallbackInfo) {
        if (AxionEditorMode.shouldBlockCursorUnlock()) {
            ci.cancel()
        }
    }

    @Inject(method = ["onScroll(JDD)V"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleScroll(window: Long, horizontal: Double, vertical: Double, ci: CallbackInfo) {
        val client = getClient()
        if (AxionEditorMode.isActive() && client.gui.screen() == null) {
            // The editor owns the wheel: feed panels first, then swallow so
            // hotbar cycling never fires mid-editing.
            AxionEditorUi.onMouseScroll(vertical)
            ci.cancel()
            return
        }
        if (client.gui.screen() != null) return

        val holdingTool = LitematicaCompat.isHoldingConfiguredTool(client)

        if (holdingTool) {
            return
        }

        val litematicaLoaded = LitematicaCompat.isAvailable()
        val altHeld = AxionModifierKeys.isAltDown(client)
        val ctrlHeld = AxionModifierKeys.isControlDown(client)

        // Litematica reserves Ctrl+scroll for its own tool handling, but the
        // axion slot outranks that pass-through: with the slot active the wheel
        // belongs to Axion (symmetry nudging, brush sizing), otherwise
        // Ctrl+scroll would dump the user onto the vanilla hotbar.
        if (litematicaLoaded && ctrlHeld && !altHeld && !AxionToolSelectionController.isAxionSlotActive()) {
            return
        }

        val player = client.player ?: return
        when (val outcome = AxionInteractionRouter.handleScroll(
            client = client,
            currentVanillaSlot = player.inventory.selectedSlot,
            scrollAmount = vertical,
            altHeld = altHeld,
            ctrlHeld = ctrlHeld,
        )) {
            axion.client.tool.AxionToolSelectionController.ScrollOutcome.PassThrough -> Unit
            axion.client.tool.AxionToolSelectionController.ScrollOutcome.Consumed -> ci.cancel()
            is axion.client.tool.AxionToolSelectionController.ScrollOutcome.SelectVanilla -> {
                player.inventory.selectedSlot = outcome.slot
                ci.cancel()
            }
        }
    }

}

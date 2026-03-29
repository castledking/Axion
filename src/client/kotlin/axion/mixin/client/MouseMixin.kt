package axion.mixin.client

import axion.client.compat.LitematicaCompat
import axion.client.hotbar.AxionAltMenuController
import axion.client.input.AxionInteractionRouter
import axion.client.input.AxionModifierKeys
import axion.client.mode.ClientModeController
import net.minecraft.client.MinecraftClient
import net.minecraft.client.Mouse
import net.minecraft.client.input.MouseInput
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.lwjgl.glfw.GLFW

@Mixin(Mouse::class)
abstract class MouseMixin {
    @Shadow
    private lateinit var client: MinecraftClient

    @Inject(method = ["onMouseButton(JIII)V"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandleMouseButtonLegacy(window: Long, button: Int, action: Int, mods: Int, ci: CallbackInfo) {
        axionHandleMouseButton(button, action, ci)
    }

    @Inject(
        method = ["onMouseButton(JLnet/minecraft/client/input/MouseInput;I)V"],
        at = [At("HEAD")],
        cancellable = true,
        require = 0,
    )
    private fun axionHandleMouseButtonModern(window: Long, mouseInput: MouseInput, action: Int, ci: CallbackInfo) {
        axionHandleMouseButton(mouseInput.button(), action, ci)
    }

    private fun axionHandleMouseButton(button: Int, action: Int, ci: CallbackInfo) {
        if (AxionAltMenuController.handleMouseButton(client, button, action)) {
            ci.cancel()
            return
        }

        if (client.currentScreen != null || action != GLFW.GLFW_PRESS) {
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

    @Inject(method = ["onMouseScroll"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleScroll(window: Long, horizontal: Double, vertical: Double, ci: CallbackInfo) {
        if (client.currentScreen != null) {
            return
        }

        if (AxionModifierKeys.isAltDown(client) && LitematicaCompat.isHoldingConfiguredTool(client)) {
            return
        }

        val player = client.player ?: return
        when (val outcome = AxionInteractionRouter.handleScroll(
            client = client,
            currentVanillaSlot = player.inventory.selectedSlot,
            scrollAmount = vertical,
            altHeld = AxionModifierKeys.isAltDown(client),
            ctrlHeld = AxionModifierKeys.isControlDown(),
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

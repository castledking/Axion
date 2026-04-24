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

        // For infinite reach without fast place, let vanilla handle the event
        // so that doItemUse is called and continuous placement works
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT &&
            ClientModeController.shouldLetVanillaHandleSecondaryAction(client)) {
            return
        }

        // For fast place mode, let vanilla handle so doItemUse is called
        // which triggers our mixin and enables manual key tracking
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT &&
            ClientModeController.isFastPlaceEnabled(client)) {
            return
        }

        // For infinite reach without bulldozer, let vanilla handle the event
        // so that doAttack is called and continuous breaking works
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
            ClientModeController.shouldLetVanillaHandlePrimaryAction(client)) {
            return
        }

        // For bulldozer + infinite reach, also let vanilla handle for continuous multi-block breaking
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

    @Inject(method = ["onMouseScroll"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleScroll(window: Long, horizontal: Double, vertical: Double, ci: CallbackInfo) {
        if (client.currentScreen != null) {
            return
        }

        if (LitematicaCompat.isHoldingConfiguredTool(client)) {
            if (AxionModifierKeys.isAltDown(client) || AxionModifierKeys.isControlDown(client)) {
                return
            }
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

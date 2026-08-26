package axion.mixin.client

import axion.client.compat.LitematicaCompat
import axion.client.hotbar.AxionAltMenuController
import axion.client.input.AxionInteractionRouter
import axion.client.input.AxionModifierKeys
import axion.client.mode.ClientModeController
import axion.mixin.compat.currentScreenOf
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.input.MouseButtonInfo
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.lwjgl.glfw.GLFW

/**
 * 1.21.9+ MouseHandler.onButton injection. Split out of [MouseMixin] because
 * it imports net.minecraft.client.input.MouseButtonInfo, which does not exist
 * on 1.21.6-1.21.8 — the 1.21.6-8 jar strips this class from the mixin config
 * so it is never loaded there.
 */
@Mixin(MouseHandler::class)
abstract class MouseModernMixin {
    private fun getClient(): Minecraft = Minecraft.getInstance()

    // Modern version with MouseButtonInfo
    @Inject(
        method = ["onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V"],
        at = [At("HEAD")],
        cancellable = true,
    )
    private fun axionHandleMouseButtonModern(window: Long, mouseInput: MouseButtonInfo, action: Int, ci: CallbackInfo) {
        val client = getClient()
        if (AxionAltMenuController.handleMouseButton(client, mouseInput.button(), action)) {
            ci.cancel()
            return
        }

        if (currentScreenOf(client) != null || action != GLFW.GLFW_PRESS) {
            return
        }

        // For infinite reach without fast place, let vanilla handle the event
        // so that doItemUse is called and continuous placement works
        if (mouseInput.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT &&
            ClientModeController.shouldLetVanillaHandleSecondaryAction(client)) {
            return
        }

        // For fast place mode, let vanilla handle so doItemUse is called
        // which triggers our mixin and enables manual key tracking
        if (mouseInput.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT &&
            ClientModeController.isFastPlaceEnabled(client)) {
            return
        }

        // For infinite reach without bulldozer, let vanilla handle the event
        // so that doAttack is called and continuous breaking works
        if (mouseInput.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
            ClientModeController.shouldLetVanillaHandlePrimaryAction(client)) {
            return
        }

        // For bulldozer + infinite reach, also let vanilla handle for continuous multi-block breaking
        if (mouseInput.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
            ClientModeController.shouldLetVanillaHandleBulldozerInfiniteReach(client)) {
            return
        }

        val consumed = when (mouseInput.button()) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> ClientModeController.consumePrimaryAction(client)
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> ClientModeController.consumeSecondaryAction(client)
            else -> false
        }
        if (consumed) {
            ci.cancel()
        }
    }

}

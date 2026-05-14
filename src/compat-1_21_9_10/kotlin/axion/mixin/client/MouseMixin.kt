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
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.lwjgl.glfw.GLFW

/**
 * 1.21.11-specific MouseMixin with modern MouseInput signature support.
 */
@Mixin(Mouse::class)
abstract class MouseMixin {
    private fun getClient(): MinecraftClient = MinecraftClient.getInstance()

    // Modern version with MouseInput
    @Inject(
        method = ["onMouseButton(JLnet/minecraft/client/input/MouseInput;I)V"],
        at = [At("HEAD")],
        cancellable = true,
    )
    private fun axionHandleMouseButtonModern(window: Long, mouseInput: MouseInput, action: Int, ci: CallbackInfo) {
        val client = getClient()
        if (AxionAltMenuController.handleMouseButton(client, mouseInput.button(), action)) {
            ci.cancel()
            return
        }

        if (hasCurrentScreen(client) || action != GLFW.GLFW_PRESS) {
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

    // Yarn name: onMouseScroll (1.21.x)
    @Inject(method = ["onMouseScroll"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleScroll(window: Long, horizontal: Double, vertical: Double, ci: CallbackInfo) {
        val client = getClient()
        if (hasCurrentScreen(client)) {
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

    private fun hasCurrentScreen(client: MinecraftClient): Boolean {
        val screenField = client.javaClass.fields.firstOrNull { it.name == "currentScreen" || it.name == "screen" }
            ?: client.javaClass.declaredFields.firstOrNull { it.name == "currentScreen" || it.name == "screen" }?.also { it.isAccessible = true }
            ?: return false
        return runCatching { screenField.get(client) != null }.getOrDefault(false)
    }
}

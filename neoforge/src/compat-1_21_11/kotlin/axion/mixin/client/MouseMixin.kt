package axion.mixin.client

import axion.client.compat.LitematicaCompat
import axion.client.hotbar.AxionAltMenuController
import axion.client.input.AxionInteractionRouter
import axion.client.input.AxionModifierKeys
import axion.client.mode.ClientModeController
import axion.mixin.compat.currentScreenOf
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.lwjgl.glfw.GLFW

/**
 * 1.21.11-specific MouseMixin with modern MouseButtonInfo signature support.
 */
@Mixin(MouseHandler::class)
abstract class MouseMixin {
    private fun getClient(): Minecraft = Minecraft.getInstance()

    // Legacy signature (MC 1.21.6-1.21.8): GLFW callback ints, mojmap onPress.
    // require=0 keeps this a silent no-op on modern jars where the legacy
    // mixin config entry is stripped instead.
    @Inject(
        method = ["onPress"],
        at = [At("HEAD")],
        cancellable = true,
        require = 0,
    )
    private fun axionHandleMouseButtonLegacy(window: Long, button: Int, action: Int, mods: Int, ci: CallbackInfo) {
        val client = getClient()
        if (AxionAltMenuController.handleMouseButton(client, button, action)) {
            ci.cancel()
            return
        }

        if (currentScreenOf(client) != null || action != GLFW.GLFW_PRESS) {
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

    // Yarn name: onMouseScroll (1.21.x)
    @Inject(method = ["onScroll"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleScroll(window: Long, horizontal: Double, vertical: Double, ci: CallbackInfo) {
        val client = getClient()
        if (currentScreenOf(client) != null) {
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

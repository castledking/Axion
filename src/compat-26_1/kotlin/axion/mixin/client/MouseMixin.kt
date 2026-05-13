package axion.mixin.client

import axion.client.compat.LitematicaCompat
import axion.client.input.AxionInteractionRouter
import axion.client.input.AxionModifierKeys
import net.minecraft.client.MinecraftClient
import net.minecraft.client.MouseHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * 26.1.x-specific MouseMixin: injects into the private onScroll method of MouseHandler.
 * The method is `private void onScroll(long, double, double)` in the official namespace.
 */
@Mixin(MouseHandler::class)
abstract class MouseMixin {
    private fun getClient(): MinecraftClient = MinecraftClient.getInstance()

    @Inject(method = ["onScroll(JDD)V"], at = [At("HEAD")], cancellable = true)
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

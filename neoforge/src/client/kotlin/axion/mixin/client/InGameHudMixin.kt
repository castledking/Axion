package axion.mixin.client

import axion.client.hotbar.AxionHotbarPresentation
import axion.client.hotbar.SavedHotbarController
import axion.client.tool.AxionToolSelectionController
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.hud.InGameHud
import net.minecraft.client.render.RenderTickCounter
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Group
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(InGameHud::class)
abstract class InGameHudMixin {
    @Inject(method = ["renderHotbar"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressVanillaHotbar(context: DrawContext, tickCounter: RenderTickCounter, ci: CallbackInfo) {
        val client = MinecraftClient.getInstance()
        if (!AxionToolSelectionController.isAxionSelected() && SavedHotbarController.isOverlayActive(client)) {
            ci.cancel()
        }
    }

    @Group(name = "axionVanillaHotbarSelector", min = 1, max = 1)
    @ModifyExpressionValue(
        method = ["renderHotbar"],
        at = [At(
            value = "FIELD",
            target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I",
        )],
        require = 0,
    )
    private fun axionHideLegacyVanillaSelector(originalSlot: Int): Int = selectorSlot(originalSlot)

    @Group(name = "axionVanillaHotbarSelector", min = 1, max = 1)
    @ModifyExpressionValue(
        method = ["renderHotbar"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;getSelectedSlot()I",
        )],
        require = 0,
    )
    private fun axionHideModernVanillaSelector(originalSlot: Int): Int = selectorSlot(originalSlot)

    private fun selectorSlot(originalSlot: Int): Int = AxionHotbarPresentation.vanillaSelectorSlot(
        originalSlot = originalSlot,
        selectionState = AxionToolSelectionController.currentState(),
        creativeAllowed = AxionToolSelectionController.isCreativeModeAllowed(),
    )
}

package axion.mixin.client

import axion.client.hotbar.AxionHotbarPresentation
import axion.client.hotbar.SavedHotbarController
import axion.client.itemStack.AxionToolSelectionController
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.Gui
import net.minecraft.client.DeltaTracker
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Group
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Gui::class)
abstract class InGameHudMixin {
    @Inject(method = ["renderHotbar"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressVanillaHotbar(context: GuiGraphics, tickCounter: DeltaTracker, ci: CallbackInfo) {
        val client = Minecraft.getInstance()
        if (!AxionToolSelectionController.isAxionSelected() && SavedHotbarController.isOverlayActive(client)) {
            ci.cancel()
        }
    }

    @Group(name = "axionVanillaHotbarSelector", min = 1, max = 1)
    @ModifyExpressionValue(
        method = ["renderHotbar"],
        at = [At(
            value = "FIELD",
            target = "Lnet.minecraft.world.entity.player.Inventory;selectedSlot:I",
        )],
        require = 0,
    )
    private fun axionHideLegacyVanillaSelector(originalSlot: Int): Int = selectorSlot(originalSlot)

    @Group(name = "axionVanillaHotbarSelector", min = 1, max = 1)
    @ModifyExpressionValue(
        method = ["renderHotbar"],
        at = [At(
            value = "INVOKE",
            target = "Lnet.minecraft.world.entity.player.Inventory;getSelectedSlot()I",
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

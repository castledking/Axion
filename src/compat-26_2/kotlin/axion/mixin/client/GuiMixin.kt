package axion.mixin.client

import axion.client.hotbar.AxionHotbarPresentation
import axion.client.hotbar.SavedHotbarController
import axion.client.tool.AxionToolSelectionController
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import net.minecraft.client.DeltaTracker
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Hud
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

// 26.2 renamed the HUD class from Gui to Hud and gave the name Gui to the new
// screen manager. @Mixin(Gui::class) still compiles and still resolves against
// 26.2 — it just targets the wrong class and never applies, silently leaving
// the vanilla hotbar drawn underneath Axion's. extractItemHotbar itself is
// unchanged; only its owner moved.
@Mixin(Hud::class)
abstract class GuiMixin {
    @Inject(method = ["extractItemHotbar"], at = [At("HEAD")], cancellable = true)
    private fun axionSuppressVanillaHotbar(
        extractor: GuiGraphicsExtractor,
        deltaTracker: DeltaTracker,
        ci: CallbackInfo,
    ) {
        val client = MinecraftClient.getInstance()
        if (!AxionToolSelectionController.isAxionSelected() && SavedHotbarController.isOverlayActive(client)) {
            ci.cancel()
        }
    }

    @ModifyExpressionValue(
        method = ["extractItemHotbar"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I",
        )],
    )
    private fun axionHideVanillaSelector(originalSlot: Int): Int = AxionHotbarPresentation.vanillaSelectorSlot(
        originalSlot = originalSlot,
        selectionState = AxionToolSelectionController.currentState(),
        creativeAllowed = AxionToolSelectionController.isCreativeModeAllowed(),
    )
}

package axion.mixin.client

import axion.client.hotbar.SavedHotbarController
import axion.client.tool.AxionToolSelectionController
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.hud.InGameHud
import net.minecraft.client.render.RenderTickCounter
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
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
}

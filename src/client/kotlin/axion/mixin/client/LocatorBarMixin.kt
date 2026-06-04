package axion.mixin.client

import axion.client.hotbar.AxionAltMenuController
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.hud.bar.LocatorBar
import net.minecraft.client.render.RenderTickCounter
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(LocatorBar::class)
abstract class LocatorBarMixin {
    @Inject(method = ["renderBar"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressLocatorBar(context: DrawContext, tickCounter: RenderTickCounter, ci: CallbackInfo) {
        if (AxionAltMenuController.isAnyAltOverlayActive(MinecraftClient.getInstance())) {
            ci.cancel()
        }
    }

    @Inject(method = ["renderAddons"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressLocatorAddons(context: DrawContext, tickCounter: RenderTickCounter, ci: CallbackInfo) {
        if (AxionAltMenuController.isAnyAltOverlayActive(MinecraftClient.getInstance())) {
            ci.cancel()
        }
    }
}

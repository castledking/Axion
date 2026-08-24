package axion.mixin.client

import axion.client.editor.AxionEditorMode
import axion.client.hotbar.AxionHotbarPresentation
import axion.client.hotbar.SavedHotbarController
import axion.client.tool.AxionToolSelectionController
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import net.minecraft.client.DeltaTracker
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.hit.HitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Gui::class)
abstract class GuiMixin {
    // Axiom-style editor: the crosshair disappears while the editor owns the
    // cursor, and comes back only while right click drags the camera angle.
    // The drag check matters: while dragging, isActive() is still true, so an
    // unconditional cancel would swallow extractCrosshair before vanilla ever
    // consults canRenderCrosshairForSpectator below.
    @Inject(method = ["extractCrosshair"], at = [At("HEAD")], cancellable = true)
    private fun axionSuppressEditorCrosshair(
        extractor: GuiGraphicsExtractor,
        deltaTracker: DeltaTracker,
        ci: CallbackInfo,
    ) {
        if (AxionEditorMode.isActive() && !AxionEditorMode.isDraggingCamera) {
            ci.cancel()
        }
    }

    @Inject(method = ["canRenderCrosshairForSpectator"], at = [At("HEAD")], cancellable = true)
    private fun axionShowCrosshairDuringDrag(hitResult: HitResult, cir: CallbackInfoReturnable<Boolean>) {
        if (AxionEditorMode.isDraggingCamera) {
            cir.returnValue = true
        }
    }

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

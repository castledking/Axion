package axion.mixin.client

import axion.client.render.gpu.ChunkedPreviewLifecycle
import net.minecraft.client.DeltaTracker
import net.minecraft.client.renderer.GameRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Fabric's END_MAIN callback runs before Minecraft composites glowing entity
 * outlines. Flush Axion's direct preview after that composite so dense Paper
 * protection visualizers cannot paint over it.
 */
@Mixin(GameRenderer::class)
abstract class GameRendererPostOutlineMixin {
    @Inject(
        method = ["renderLevel(Lnet/minecraft/client/DeltaTracker;)V"],
        at = [At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
            ordinal = 0,
            shift = At.Shift.BEFORE,
        )],
    )
    private fun axionCaptureSceneDepthBeforeHand(
        deltaTracker: DeltaTracker,
        ci: CallbackInfo,
    ) {
        ChunkedPreviewLifecycle.captureSceneDepthBeforeHand()
    }

    @Inject(
        method = ["render(Lnet/minecraft/client/DeltaTracker;Z)V"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
            shift = At.Shift.AFTER,
        )],
    )
    private fun axionFlushPreviewAfterEntityOutline(
        deltaTracker: DeltaTracker,
        renderLevel: Boolean,
        ci: CallbackInfo,
    ) {
        ChunkedPreviewLifecycle.flushPostWorldDraws()
    }

    /**
     * Backup flush at the end of [GameRenderer.render] so deferred preview
     * draws are never stranded when [doEntityOutline] is skipped (e.g. mod
     * incompatibility). Safe to call redundantly because
     * [ChunkedPreviewLifecycle.flushPostWorldDraws] is a no-op when empty.
     */
    @Inject(
        method = ["render(Lnet/minecraft/client/DeltaTracker;Z)V"],
        at = [At("TAIL")],
    )
    private fun axionFlushPreviewRenderTail(
        deltaTracker: DeltaTracker,
        renderLevel: Boolean,
        ci: CallbackInfo,
    ) {
        ChunkedPreviewLifecycle.flushPostWorldDraws()
    }
}

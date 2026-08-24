package axion.mixin.client

import axion.client.render.WorldRenderCompat
import net.minecraft.client.Camera
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.state.LevelRenderState
import com.mojang.blaze3d.vertex.PoseStack
import org.slf4j.LoggerFactory
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * 1.21.11-specific WorldRendererFallbackMixin with modern LevelRenderState signature support.
 */
@Mixin(LevelRenderer::class)
abstract class WorldRendererFallbackMixin {
    private companion object {
        private val logger = LoggerFactory.getLogger(WorldRendererFallbackMixin::class.java)
        private var loggedBlockDamage = false
        private var loggedTargetOutline = false
    }

    // Modern signatures (MC 1.21.9+)

    @Inject(
        method = ["renderBlockDamage(Lcom.mojang.blaze3d.vertex.PoseStack;Lnet.minecraft.client.renderer.MultiBufferSource\$MultiBufferSource.BufferSource;Lnet.minecraft.client.renderer.state.LevelRenderState;)V"],
        at = [At("TAIL")],
    )
    private fun axionFallbackAfterBlockDamageModern(
        matrices: PoseStack,
        immediate: net.minecraft.client.render.Immediate,
        renderState: LevelRenderState,
        ci: CallbackInfo,
    ) {
        if (!loggedBlockDamage) {
            loggedBlockDamage = true
            logger.info("[Axion/Mixin] renderBlockDamage mixin called, hasFallbackCallbacks={}", WorldRenderCompat.hasFallbackCallbacks())
        }
        if (!WorldRenderCompat.hasFallbackCallbacks()) {
            return
        }
        WorldRenderCompat.dispatchFallbackCallbacks(immediate, matrices)
    }

    @Inject(
        method = ["renderTargetBlockOutline(Lnet.minecraft.client.renderer.MultiBufferSource\$MultiBufferSource.BufferSource;Lcom.mojang.blaze3d.vertex.PoseStack;ZLnet.minecraft.client.renderer.state.LevelRenderState;)V"],
        at = [At("TAIL")],
    )
    private fun axionFallbackAfterTargetOutlineModern(
        immediate: net.minecraft.client.render.Immediate,
        matrices: PoseStack,
        renderHitOutline: Boolean,
        renderState: LevelRenderState,
        ci: CallbackInfo,
    ) {
        if (!loggedTargetOutline) {
            loggedTargetOutline = true
            logger.info("[Axion/Mixin] renderTargetBlockOutline mixin called, hasFallbackCallbacks={}", WorldRenderCompat.hasFallbackCallbacks())
        }
        if (!WorldRenderCompat.hasFallbackCallbacks()) {
            return
        }
        WorldRenderCompat.dispatchFallbackCallbacks(immediate, matrices)
    }
}

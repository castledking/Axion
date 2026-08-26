package axion.mixin.client

import axion.client.render.WorldRenderCompat
import net.minecraft.client.Camera
import net.minecraft.client.renderer.LevelRenderer
import com.mojang.blaze3d.vertex.PoseStack
import org.slf4j.LoggerFactory
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Pre-1.21.9 LevelRenderer hook names (mojmap renderBlockDestroyAnimation /
 * renderBlockOutline with the Camera signature). Only listed in the
 * 1.21.6-1.21.8 jar's mixin config — on modern jars the class is inert
 * because every type it imports still exists, but the injections are
 * require=0 in case the config ever ships together.
 */
@Mixin(LevelRenderer::class)
abstract class WorldRendererFallbackLegacyMixin {
    private companion object {
        private val logger = LoggerFactory.getLogger(WorldRendererFallbackLegacyMixin::class.java)
    }

    @Inject(
        method = ["renderBlockDestroyAnimation"],
        at = [At("TAIL")],
        require = 0,
    )
    private fun axionFallbackAfterBlockDamageLegacy(
        matrices: PoseStack,
        camera: Camera,
        immediate: net.minecraft.client.renderer.MultiBufferSource.BufferSource,
        ci: CallbackInfo,
    ) {
        if (!WorldRenderCompat.hasFallbackCallbacks()) {
            return
        }
        logger.info("[Axion/Mixin] legacy renderBlockDamage dispatched")
        WorldRenderCompat.dispatchFallbackCallbacks(immediate, matrices)
    }

    @Inject(
        method = ["renderBlockOutline"],
        at = [At("TAIL")],
        require = 0,
    )
    private fun axionFallbackAfterTargetOutlineLegacy(
        camera: Camera,
        immediate: net.minecraft.client.renderer.MultiBufferSource.BufferSource,
        matrices: PoseStack,
        renderHitOutline: Boolean,
        ci: CallbackInfo,
    ) {
        if (!WorldRenderCompat.hasFallbackCallbacks()) {
            return
        }
        logger.info("[Axion/Mixin] legacy renderTargetBlockOutline dispatched")
        WorldRenderCompat.dispatchFallbackCallbacks(immediate, matrices)
    }
}

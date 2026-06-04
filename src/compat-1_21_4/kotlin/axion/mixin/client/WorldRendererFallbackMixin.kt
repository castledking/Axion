package axion.mixin.client

import axion.client.render.WorldRenderCompat
import net.minecraft.client.render.Camera
import net.minecraft.client.render.WorldRenderer
import net.minecraft.client.util.math.MatrixStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * 1.21.5-specific WorldRendererFallbackMixin with legacy Camera signature.
 */
@Mixin(WorldRenderer::class)
abstract class WorldRendererFallbackMixin {
    // Legacy signatures (MC 1.21.5 and earlier)

    @Inject(
        method = ["renderBlockDamage"],
        at = [At("TAIL")],
    )
    private fun axionFallbackAfterBlockDamage(
        matrices: MatrixStack,
        camera: Camera,
        immediate: net.minecraft.client.render.Immediate,
        ci: CallbackInfo,
    ) {
        if (!WorldRenderCompat.hasFallbackCallbacks()) {
            return
        }
        WorldRenderCompat.dispatchFallbackCallbacks(immediate, matrices)
    }

    @Inject(
        method = ["renderTargetBlockOutline"],
        at = [At("TAIL")],
    )
    private fun axionFallbackAfterTargetOutline(
        camera: Camera,
        immediate: net.minecraft.client.render.Immediate,
        matrices: MatrixStack,
        renderHitOutline: Boolean,
        ci: CallbackInfo,
    ) {
        if (!WorldRenderCompat.hasFallbackCallbacks()) {
            return
        }
        WorldRenderCompat.dispatchFallbackCallbacks(immediate, matrices)
    }
}

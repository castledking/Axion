package axion.mixin.client

import axion.client.render.WorldRenderCompat
import net.minecraft.client.render.Camera
import net.minecraft.client.render.WorldRenderer
import net.minecraft.client.render.state.WorldRenderState
import net.minecraft.client.util.math.MatrixStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * 1.21.11-specific WorldRendererFallbackMixin with modern WorldRenderState signature support.
 */
@Mixin(WorldRenderer::class)
abstract class WorldRendererFallbackMixin {
    // Modern signatures (MC 1.21.9+)

    @Inject(
        method = ["renderBlockDamage(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider\$Immediate;Lnet/minecraft/client/render/state/WorldRenderState;)V"],
        at = [At("TAIL")],
    )
    private fun axionFallbackAfterBlockDamageModern(
        matrices: MatrixStack,
        immediate: net.minecraft.client.render.Immediate,
        renderState: WorldRenderState,
        ci: CallbackInfo,
    ) {
        if (!WorldRenderCompat.hasFallbackCallbacks()) {
            return
        }
        WorldRenderCompat.dispatchFallbackCallbacks(immediate, matrices)
    }

    @Inject(
        method = ["renderTargetBlockOutline(Lnet/minecraft/client/render/VertexConsumerProvider\$Immediate;Lnet/minecraft/client/util/math/MatrixStack;ZLnet/minecraft/client/render/state/WorldRenderState;)V"],
        at = [At("TAIL")],
    )
    private fun axionFallbackAfterTargetOutlineModern(
        immediate: net.minecraft.client.render.Immediate,
        matrices: MatrixStack,
        renderHitOutline: Boolean,
        renderState: WorldRenderState,
        ci: CallbackInfo,
    ) {
        if (!WorldRenderCompat.hasFallbackCallbacks()) {
            return
        }
        WorldRenderCompat.dispatchFallbackCallbacks(immediate, matrices)
    }
}

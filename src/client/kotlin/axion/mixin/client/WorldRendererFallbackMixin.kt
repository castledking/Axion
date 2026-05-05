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
 * Cross-version fallback mixin for end-of-frame rendering.
 *
 * `WorldRenderer.renderBlockDamage` and `renderTargetBlockOutline` changed
 * signatures in 1.21.9: previously they took a `Camera`, after they take a
 * `WorldRenderState`. We register injections for both signatures with
 * `require = 0`; only the matching one applies on a given runtime.
 *
 * This mixin only runs when the Fabric `WorldRenderEvents` API is unavailable
 * (older Fabric versions or runtimes where the events class is missing).
 */
@Mixin(WorldRenderer::class)
abstract class WorldRendererFallbackMixin {
    // ----- Modern signatures (MC 1.21.9+) -----

    @Inject(
        method = ["renderBlockDamage(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider\$Immediate;Lnet/minecraft/client/render/state/WorldRenderState;)V"],
        at = [At("TAIL")],
        require = 0,
    )
    private fun axionFallbackAfterBlockDamageModern(
        matrices: MatrixStack,
        immediate: net.minecraft.client.render.VertexConsumerProvider.Immediate,
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
        require = 0,
    )
    private fun axionFallbackAfterTargetOutlineModern(
        immediate: net.minecraft.client.render.VertexConsumerProvider.Immediate,
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

    // ----- Legacy signatures (MC 1.21.5 - 1.21.8) -----

    @Inject(
        method = ["renderBlockDamage(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/VertexConsumerProvider\$Immediate;)V"],
        at = [At("TAIL")],
        require = 0,
    )
    private fun axionFallbackAfterBlockDamageLegacy(
        matrices: MatrixStack,
        camera: Camera,
        immediate: net.minecraft.client.render.VertexConsumerProvider.Immediate,
        ci: CallbackInfo,
    ) {
        if (!WorldRenderCompat.hasFallbackCallbacks()) {
            return
        }
        WorldRenderCompat.dispatchFallbackCallbacks(immediate, matrices)
    }

    @Inject(
        method = ["renderTargetBlockOutline(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/VertexConsumerProvider\$Immediate;Lnet/minecraft/client/util/math/MatrixStack;Z)V"],
        at = [At("TAIL")],
        require = 0,
    )
    private fun axionFallbackAfterTargetOutlineLegacy(
        camera: Camera,
        immediate: net.minecraft.client.render.VertexConsumerProvider.Immediate,
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

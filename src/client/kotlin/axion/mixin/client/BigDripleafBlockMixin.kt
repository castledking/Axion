package axion.mixin.client

import axion.client.compat.PhantomService
import com.llamalad7.mixinextras.sugar.Local
import net.minecraft.block.BigDripleafBlock
import net.minecraft.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Keeps a big dripleaf flat under a phantom player.
 *
 * Cancelling the collision skips the tilt schedule entirely, so the leaf never
 * reaches the PARTIAL/FULL states that drop whoever is standing on it.
 */
@Mixin(BigDripleafBlock::class)
class BigDripleafBlockMixin {
    // onEntityCollision gained an EntityCollisionHandler in 1.21.5 and a boolean in 1.21.10, so
    // the args are captured by type rather than declared positionally to stay arity-agnostic.
    @Inject(method = ["onEntityCollision"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelDripleafTilt(ci: CallbackInfo, @Local(argsOnly = true) entity: Entity) {
        axionPhantomCancelDripleafTiltImpl(ci, entity)
    }

    // 26.x official namespace: entityInside
    @Inject(method = ["entityInside"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelDripleafTiltOfficial(ci: CallbackInfo, @Local(argsOnly = true) entity: Entity) {
        axionPhantomCancelDripleafTiltImpl(ci, entity)
    }

    private fun axionPhantomCancelDripleafTiltImpl(ci: CallbackInfo, entity: Entity) {
        if (PhantomService.isEnabledFor(entity)) {
            ci.cancel()
        }
    }
}

package axion.mixin.client

import axion.client.compat.PhantomService
import com.llamalad7.mixinextras.sugar.Local
import net.minecraft.block.TripwireBlock
import net.minecraft.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(TripwireBlock::class)
class TripwireBlockMixin {
    // onEntityCollision gained an EntityCollisionHandler in 1.21.5 and a boolean in 1.21.10, so
    // the args are captured by type rather than declared positionally to stay arity-agnostic.
    @Inject(method = ["onEntityCollision"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelTripwire(ci: CallbackInfo, @Local(argsOnly = true) entity: Entity) {
        axionPhantomCancelTripwireImpl(ci, entity)
    }

    // 26.x official namespace: entityInside
    @Inject(method = ["entityInside"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelTripwireOfficial(ci: CallbackInfo, @Local(argsOnly = true) entity: Entity) {
        axionPhantomCancelTripwireImpl(ci, entity)
    }

    private fun axionPhantomCancelTripwireImpl(ci: CallbackInfo, entity: Entity) {
        if (PhantomService.isEnabledFor(entity)) {
            ci.cancel()
        }
    }
}

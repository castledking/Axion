package axion.mixin.client

import axion.client.compat.PhantomService
import net.minecraft.block.BlockState
import net.minecraft.block.TripwireBlock
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(TripwireBlock::class)
class TripwireBlockMixin {
    @Inject(method = ["onEntityCollision"], at = [At("HEAD")], cancellable = true)
    fun axionPhantomCancelTripwire(state: BlockState, world: World, pos: BlockPos, entity: Entity, ci: CallbackInfo) {
        if (PhantomService.isEnabledFor(entity)) {
            ci.cancel()
        }
    }
}

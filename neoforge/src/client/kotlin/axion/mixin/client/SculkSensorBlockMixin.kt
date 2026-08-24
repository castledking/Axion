package axion.mixin.client

import axion.client.compat.PhantomService
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.SculkSensorBlock
import net.minecraft.world.entity.Entity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(SculkSensorBlock::class)
class SculkSensorBlockMixin {
    @Inject(method = ["onSteppedOn"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelSculkSensor(world: Level, pos: BlockPos, state: BlockState, entity: Entity, ci: CallbackInfo) {
        if (PhantomService.isEnabledFor(entity)) {
            ci.cancel()
        }
    }

    // 26.x official namespace: stepOn
    @Inject(method = ["stepOn"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelSculkSensorOfficial(world: Level, pos: BlockPos, state: BlockState, entity: Entity, ci: CallbackInfo) {
        if (PhantomService.isEnabledFor(entity)) {
            ci.cancel()
        }
    }
}

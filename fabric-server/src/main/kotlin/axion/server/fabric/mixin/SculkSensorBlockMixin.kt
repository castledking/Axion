package axion.server.fabric.mixin

import axion.server.fabric.AxionFabricPhantomService
import net.minecraft.block.BlockState
import net.minecraft.block.SculkSensorBlock
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(SculkSensorBlock::class)
class SculkSensorBlockMixin {
    @Inject(method = ["onSteppedOn"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelSculkSensor(world: World, pos: BlockPos, state: BlockState, entity: Entity, ci: CallbackInfo) {
        if (AxionFabricPhantomService.isPhantom(entity)) {
            ci.cancel()
        }
    }
}

package axion.mixin.client

import axion.client.compat.PhantomService
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.RedStoneOreBlock
import net.minecraft.world.entity.Entity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(RedStoneOreBlock::class)
class RedstoneOreBlockMixin {
    @Inject(method = ["onSteppedOn"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelRedstoneOre(world: Level, pos: BlockPos, state: BlockState, entity: Entity, ci: CallbackInfo) {
        if (PhantomService.isEnabledFor(entity)) {
            ci.cancel()
        }
    }

    // 26.x official namespace: stepOn
    @Inject(method = ["stepOn"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelRedstoneOreOfficial(world: Level, pos: BlockPos, state: BlockState, entity: Entity, ci: CallbackInfo) {
        if (PhantomService.isEnabledFor(entity)) {
            ci.cancel()
        }
    }
}

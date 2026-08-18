package axion.server.fabric.mixin

import axion.server.fabric.AxionFabricPhantomService
import com.llamalad7.mixinextras.sugar.Local
import net.minecraft.block.CobwebBlock
import net.minecraft.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(CobwebBlock::class)
class CobwebBlockMixin {
    @Inject(method = ["onEntityCollision"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelSlowdown(ci: CallbackInfo, @Local(argsOnly = true) entity: Entity) {
        if (AxionFabricPhantomService.isPhantom(entity)) {
            ci.cancel()
        }
    }
}

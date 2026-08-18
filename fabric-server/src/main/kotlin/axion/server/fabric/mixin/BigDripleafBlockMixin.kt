package axion.server.fabric.mixin

import axion.server.fabric.AxionFabricPhantomService
import com.llamalad7.mixinextras.sugar.Local
import net.minecraft.block.BigDripleafBlock
import net.minecraft.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Server half of Phantom for big dripleaf. The tilt is authoritative here, so
 * cancelling only on the client would still drop the player a tick later.
 */
@Mixin(BigDripleafBlock::class)
class BigDripleafBlockMixin {
    @Inject(method = ["onEntityCollision"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelDripleafTilt(ci: CallbackInfo, @Local(argsOnly = true) entity: Entity) {
        if (AxionFabricPhantomService.isPhantom(entity)) {
            ci.cancel()
        }
    }
}

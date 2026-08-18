package axion.server.fabric.mixin

import axion.server.fabric.AxionFabricPhantomService
import com.llamalad7.mixinextras.sugar.Local
import net.minecraft.block.AbstractPressurePlateBlock
import net.minecraft.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Server half of Phantom for pressure plates.
 *
 * The client mixin only keeps the local simulation quiet; the plate state and the
 * redstone it drives are decided here, so both halves have to cancel.
 */
@Mixin(AbstractPressurePlateBlock::class)
class AbstractPressurePlateBlockMixin {
    // The trailing args of onEntityCollision differ across 1.21.x, so the entity is
    // captured by type rather than declared positionally.
    @Inject(method = ["onEntityCollision"], at = [At("HEAD")], cancellable = true, require = 0)
    fun axionPhantomCancelPressurePlate(ci: CallbackInfo, @Local(argsOnly = true) entity: Entity) {
        if (AxionFabricPhantomService.isPhantom(entity)) {
            ci.cancel()
        }
    }
}

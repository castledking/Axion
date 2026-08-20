package axion.mixin.client

import axion.client.mode.ClientModeController
import axion.client.mode.EntityNoClipSupport
import net.minecraft.entity.Entity
import net.minecraft.entity.MovementType
import net.minecraft.util.math.Vec3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Entity::class)
abstract class EntityMixin {
    @Shadow
    public abstract fun getX(): Double

    @Shadow
    public abstract fun getY(): Double

    @Shadow
    public abstract fun getZ(): Double

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun self(): Entity = this as Entity

    @Inject(method = ["move"], at = [At("HEAD")], cancellable = true)
    private fun axionApplyNoClipMovement(type: MovementType, movement: Vec3d, ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        val entity = self()
        EntityNoClipSupport.setPosition(entity, getX() + movement.x, getY() + movement.y, getZ() + movement.z)
        EntityNoClipSupport.clearCollisionFlags(entity)
        ci.cancel()
    }

    @Inject(method = ["pushOutOfBlocks"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressPushOutOfBlocks(x: Double, y: Double, z: Double, ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        ci.cancel()
    }

    // 26.x official namespace: moveTowardsClosestSpace. Without this the block a
    // no-clipping player is standing inside keeps shoving them toward open air.
    @Inject(method = ["moveTowardsClosestSpace"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressPushOutOfBlocksOfficial(x: Double, y: Double, z: Double, ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        ci.cancel()
    }
}

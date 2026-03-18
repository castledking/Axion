package axion.mixin.client

import axion.client.mode.ClientModeController
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
    public var horizontalCollision: Boolean = false

    @Shadow
    public var verticalCollision: Boolean = false

    @Shadow
    public var groundCollision: Boolean = false

    @Shadow
    public var collidedSoftly: Boolean = false

    @Shadow
    public abstract fun getX(): Double

    @Shadow
    public abstract fun getY(): Double

    @Shadow
    public abstract fun getZ(): Double

    @Shadow
    public abstract fun setPosition(x: Double, y: Double, z: Double)

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun self(): Entity = this as Entity

    @Inject(method = ["move"], at = [At("HEAD")], cancellable = true)
    private fun axionApplyNoClipMovement(type: MovementType, movement: Vec3d, ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        setPosition(getX() + movement.x, getY() + movement.y, getZ() + movement.z)
        horizontalCollision = false
        verticalCollision = false
        groundCollision = false
        collidedSoftly = false
        ci.cancel()
    }

    @Inject(method = ["pushOutOfBlocks"], at = [At("HEAD")], cancellable = true)
    private fun axionSuppressPushOutOfBlocks(x: Double, y: Double, z: Double, ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        ci.cancel()
    }
}

package axion.mixin.client

import axion.client.compat.NoClipService
import net.minecraft.entity.Entity
import net.minecraft.entity.MovementType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Keeps integrated-server collision handling from fighting the local no-clip player. */
@Mixin(Entity::class)
@Suppress("CAST_NEVER_SUCCEEDS")
class ServerEntityMixin {
    @Inject(method = ["move"], at = [At("HEAD")], cancellable = true)
    private fun axionCancelNoClipMovement(type: MovementType, movement: Vec3d, ci: CallbackInfo) {
        val entity = this as Entity
        if (entity is ServerPlayerEntity && NoClipService.isEnabled(entity.uuid)) {
            ci.cancel()
        }
    }

    @Inject(method = ["pushOutOfBlocks"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressPushOutOfBlocks(x: Double, y: Double, z: Double, ci: CallbackInfo) {
        val entity = this as Entity
        if (entity is ServerPlayerEntity && NoClipService.isEnabled(entity.uuid)) {
            ci.cancel()
        }
    }
}

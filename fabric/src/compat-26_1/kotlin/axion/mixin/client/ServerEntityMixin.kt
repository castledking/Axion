package axion.mixin.client

import axion.client.compat.NoClipService
import net.minecraft.entity.Entity
import net.minecraft.entity.MovementType
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.math.Vec3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Keeps integrated-server collision handling from fighting the local no-clip player. */
@Mixin(Entity::class)
class ServerEntityMixin {
    @Inject(method = ["move"], at = [At("HEAD")], cancellable = true)
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun axionCancelNoClipMovement(type: MovementType, movement: Vec3d, ci: CallbackInfo) {
        val entity = this as Entity
        if (entity is ServerPlayer && NoClipService.isEnabled(entity.uuid)) {
            ci.cancel()
        }
    }
}

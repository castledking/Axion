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

/**
 * Server-side mixin to cancel player movement when no-clip is active.
 * Prevents rubberbanding in singleplayer by ensuring the integrated server
 * doesn't perform collision validation on the server-side player entity.
 *
 * This mixin is in the compat package because it requires access to server-side
 * classes which are only available in version-specific compat modules.
 */
@Mixin(Entity::class)
class ServerEntityMixin {
    @Inject(method = ["move"], at = [At("HEAD")], cancellable = true)
    private fun axionCancelNoClipMovement(type: MovementType, movement: Vec3d, ci: CallbackInfo) {
        val entity = this as Entity
        if (entity !is ServerPlayerEntity) {
            return
        }
        if (!NoClipService.isEnabled(entity.uuid)) {
            return
        }

        // When no-clip is active, skip movement processing entirely.
        // The client handles position updates via its own mixin.
        ci.cancel()
    }

    @Inject(method = ["pushOutOfBlocks"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressPushOutOfBlocks(x: Double, y: Double, z: Double, ci: CallbackInfo) {
        val entity = this as Entity
        if (entity !is ServerPlayerEntity) {
            return
        }
        if (!NoClipService.isEnabled(entity.uuid)) {
            return
        }

        ci.cancel()
    }
}

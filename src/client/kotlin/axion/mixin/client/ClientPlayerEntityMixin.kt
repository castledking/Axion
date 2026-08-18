package axion.mixin.client

import axion.client.mode.ClientModeController
import axion.client.mode.EntityNoClipSupport
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.MovementType
import net.minecraft.util.math.Vec3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPlayerEntity::class)
abstract class ClientPlayerEntityMixin {
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun self(): ClientPlayerEntity = this as ClientPlayerEntity

    @Inject(method = ["tickMovement"], at = [At("HEAD")], require = 0)
    private fun axionApplyNoClipBeforeMovement(ci: CallbackInfo) {
        val player = self()
        if (!ClientModeController.isNoClipActiveFor(player)) {
            return
        }

        // The flag itself is owned by ClientModeController.applyNoClip, which sets
        // it every tick through the compat alias (noPhysics on 26.x). This hook
        // exists only to run before movement is integrated.
    }

    @Inject(method = ["move"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionApplyNoClipClientMovement(type: MovementType, movement: Vec3d, ci: CallbackInfo) {
        val player = self()
        if (!ClientModeController.isNoClipActiveFor(player)) {
            return
        }

        EntityNoClipSupport.setPosition(
            player,
            player.x + movement.x,
            player.y + movement.y,
            player.z + movement.z,
        )
        EntityNoClipSupport.clearCollisionFlags(player)
        ci.cancel()
    }

    /**
     * The client player has its own horizontal-only push-out, separate from the
     * three-argument one on Entity, and `aiStep` calls it four times per tick —
     * once per corner of the hitbox. Leaving it running is what shoves a
     * no-clipping player back out of a wall while vertical movement stays fine.
     */
    @Inject(method = ["pushOutOfBlocks"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressClientPushOutOfBlocks(x: Double, z: Double, ci: CallbackInfo) {
        axionSuppressClientPushOutOfBlocksImpl(ci)
    }

    // 26.x official namespace: moveTowardsClosestSpace(double, double).
    @Inject(method = ["moveTowardsClosestSpace"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressClientPushOutOfBlocksOfficial(x: Double, z: Double, ci: CallbackInfo) {
        axionSuppressClientPushOutOfBlocksImpl(ci)
    }

    private fun axionSuppressClientPushOutOfBlocksImpl(ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        ci.cancel()
    }
}

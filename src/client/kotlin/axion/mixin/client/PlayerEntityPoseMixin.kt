package axion.mixin.client

import axion.client.mode.ClientModeController
import net.minecraft.entity.EntityPose
import net.minecraft.entity.player.PlayerEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Forces the player into the STANDING pose while NoClip is active. Without this,
 * vanilla's [PlayerEntity.updatePose] detects that the player's standing bounding
 * box doesn't fit (because they're inside blocks) and falls back to the SWIMMING
 * pose (the crawl). The crawl pose:
 *  - cancels sprint via the `isSwimming() && !isTouchingWater()` check in
 *    LivingEntity.tickMovement, so sprint-flight boost is lost while inside blocks
 *  - shrinks the hitbox and applies crawl movement mechanics, slowing the player
 *
 * Skipping the pose update entirely (and explicitly setting STANDING) keeps the
 * player upright, preserves sprint flight, and avoids the slow-crawl side effect.
 */
@Mixin(PlayerEntity::class)
abstract class PlayerEntityPoseMixin {
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun self(): PlayerEntity = this as PlayerEntity

    @Inject(method = ["updatePose"], at = [At("HEAD")], cancellable = true)
    private fun axionForceStandingPoseInNoClip(ci: CallbackInfo) {
        val entity = self()
        if (!ClientModeController.isNoClipActiveFor(entity)) {
            return
        }

        if (entity.pose != EntityPose.STANDING) {
            entity.pose = EntityPose.STANDING
        }
        ci.cancel()
    }
}

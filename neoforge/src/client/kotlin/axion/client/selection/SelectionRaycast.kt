package axion.client.current

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.level.ClipContext

object SelectionRaycast {
    fun raycast(
        client: Minecraft,
        maxDistance: Double = AxionTargeting.DEFAULT_REACH,
    ): AxionTarget {
        val world = client.level ?: return AxionTarget.MissTarget
        val cameraEntity = client.cameraEntity ?: client.player ?: return AxionTarget.MissTarget
        val origin = cameraEntity.getEyePosition(1.0f)
        val direction = cameraEntity.getViewVector(1.0f)
        val target = origin.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance)
        val hit = world.clip(
            ClipContext(
                origin,
                target,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                cameraEntity,
            ),
        )

        return if (isBlockHit(hit)) {
            AxionTargeting.fromBlockHit(origin, hit as BlockHitResult)
        } else {
            AxionTarget.MissTarget
        }
    }
}

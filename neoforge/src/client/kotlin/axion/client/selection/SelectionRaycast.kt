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
        val world = client.world ?: return AxionTarget.MissTarget
        val cameraEntity = client.cameraEntity ?: client.player ?: return AxionTarget.MissTarget
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val direction = cameraEntity.getRotationVec(1.0f)
        val target = origin.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance)
        val hit = world.raycast(
            ClipContext(
                origin,
                target,
                ClipContext.ShapeType.OUTLINE,
                ClipContext.FluidHandling.NONE,
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

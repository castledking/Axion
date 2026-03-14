package axion.client.selection

import net.minecraft.client.MinecraftClient
import net.minecraft.world.RaycastContext

object SelectionRaycast {
    fun raycast(
        client: MinecraftClient,
        maxDistance: Double = AxionTargeting.DEFAULT_REACH,
    ): AxionTarget {
        val world = client.world ?: return AxionTarget.MissTarget
        val cameraEntity = client.cameraEntity ?: client.player ?: return AxionTarget.MissTarget
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val direction = cameraEntity.getRotationVec(1.0f)
        val target = origin.add(direction.multiply(maxDistance))
        val hit = world.raycast(
            RaycastContext(
                origin,
                target,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                cameraEntity,
            ),
        )

        return if (hit.type == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            AxionTargeting.fromBlockHit(origin, hit as net.minecraft.util.hit.BlockHitResult)
        } else {
            AxionTarget.MissTarget
        }
    }
}

package axion.client.network

import net.minecraft.util.math.Vec3d
import java.util.UUID

data class EntityMovePlan(
    val entityId: UUID,
    val fromPos: Vec3d,
    val toPos: Vec3d,
    val fromYaw: Float,
    val fromPitch: Float,
    val toYaw: Float,
    val toPitch: Float,
)

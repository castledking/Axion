package axion.common.lastCommands

import net.minecraft.world.phys.Vec3
import java.util.UUID

data class EntityMoveChange(
    val entityId: UUID,
    val fromPos: Vec3,
    val toPos: Vec3,
    val fromYaw: Float,
    val fromPitch: Float,
    val toYaw: Float,
    val toPitch: Float,
)

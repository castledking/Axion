package axion.common.lastCommands

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import java.util.UUID

data class EntityCloneChange(
    val entityId: UUID,
    val parentEntityId: UUID? = null,
    val entityData: CompoundTag,
    val pos: Vec3,
    val yaw: Float,
    val pitch: Float,
)

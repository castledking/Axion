package axion.common.history

import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.Vec3d
import java.util.UUID

data class EntityCloneChange(
    val entityId: UUID,
    val entityData: NbtCompound,
    val pos: Vec3d,
    val yaw: Float,
    val pitch: Float,
)

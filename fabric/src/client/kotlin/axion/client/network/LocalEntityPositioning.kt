package axion.client.network

import axion.common.compat.VersionCompat
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d

object LocalEntityPositioning {
    fun apply(entity: Entity, pos: Vec3d, yaw: Float, pitch: Float) {
        VersionCompat.INSTANCE.entitySetPositionAndAngles(
            entity,
            pos.x,
            pos.y,
            pos.z,
            yaw,
            pitch,
        )
        VersionCompat.INSTANCE.entityRefreshPositionAndAngles(entity)
    }
}

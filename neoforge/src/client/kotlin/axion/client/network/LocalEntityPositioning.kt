package axion.client.network

import axion.common.compat.VersionCompat
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

object LocalEntityPositioning {
    fun apply(entity: Entity, pos: Vec3, yaw: Float, pitch: Float) {
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

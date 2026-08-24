package axion.client.network

import axion.client.compat.VersionCompatImpl
import axion.common.compat.VersionCompat
import axion.common.history.EntityCloneChange
import axion.common.operation.DeleteEntitiesOperation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.Level

object LocalEntityDeleteService {
    fun plan(world: Level, operation: DeleteEntitiesOperation): List<EntityCloneChange> {
        val serverWorld = world as? ServerLevel ?: return emptyList()
        val source = operation.sourceRegion.normalized()
        val sourceMin = source.minCorner()
        val sourceMax = source.maxCorner()
        val queryBox = AABB(
            sourceMin.x.toDouble(),
            sourceMin.y.toDouble(),
            sourceMin.z.toDouble(),
            sourceMax.x + 1.0,
            sourceMax.y + 1.25,
            sourceMax.z + 1.0,
        )
        val seen = linkedSetOf<java.util.UUID>()
        val dummyEntity = serverWorld.getEntitiesOfClass(Entity::class.java, queryBox) { true }.firstOrNull()
        return VersionCompat.INSTANCE.worldGetOtherEntities(serverWorld, dummyEntity ?: return emptyList(), queryBox)
            .asSequence()
            .mapNotNull { it as? Entity }
            .map(::rootEntity)
            .filter { entity ->
                entity !is Player &&
                    !VersionCompat.INSTANCE.entityIsRemoved(entity) &&
                    VersionCompat.INSTANCE.entityGetVehicle(entity) == null &&
                    seen.add(VersionCompat.INSTANCE.entityGetUuid(entity))
            }
            .flatMap { entity ->
                captureEntityTree(entity, parentEntityId = null).asSequence()
            }
            .toList()
    }

    fun apply(world: Level, deletes: List<EntityCloneChange>) {
        val serverWorld = world as? ServerLevel ?: return
        deletes.forEach { delete ->
            serverWorld.getEntity(delete.entityId)?.discard()
        }
    }

    private fun captureEntityTree(
        entity: Entity,
        parentEntityId: java.util.UUID?,
    ): List<EntityCloneChange> {
        val snapshot = capture(entity) ?: return emptyList()
        return buildList {
            add(
                EntityCloneChange(
                    entityId = VersionCompat.INSTANCE.entityGetUuid(entity),
                    parentEntityId = parentEntityId,
                    entityData = snapshot,
                    pos = net.minecraft.world.phys.Vec3(VersionCompat.INSTANCE.entityGetX(entity), VersionCompat.INSTANCE.entityGetY(entity), VersionCompat.INSTANCE.entityGetZ(entity)),
                    yaw = VersionCompat.INSTANCE.entityGetYaw(entity),
                    pitch = VersionCompat.INSTANCE.entityGetPitch(entity),
                ),
            )
            VersionCompat.INSTANCE.entityGetPassengerList(entity).forEach { passenger ->
                val p = passenger as? Entity ?: return@forEach
                if (p !is Player && !VersionCompat.INSTANCE.entityIsRemoved(p)) {
                    addAll(captureEntityTree(p, parentEntityId = VersionCompat.INSTANCE.entityGetUuid(entity)))
                }
            }
        }
    }

    private fun capture(entity: Entity): CompoundTag? {
        return VersionCompatImpl.captureEntityData(entity)
    }

    private fun rootEntity(entity: Entity): Entity {
        var current = entity
        while (VersionCompat.INSTANCE.entityGetVehicle(current) != null) {
            current = VersionCompat.INSTANCE.entityGetVehicle(current) as Entity
        }
        return current
    }
}

package axion.client.network

import axion.client.compat.VersionCompatImpl
import axion.common.compat.VersionCompat
import axion.common.history.EntityCloneChange
import axion.common.operation.DeleteEntitiesOperation
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import net.minecraft.world.World

object LocalEntityDeleteService {
    fun plan(world: World, operation: DeleteEntitiesOperation): List<EntityCloneChange> {
        val serverWorld = world as? ServerWorld ?: return emptyList()
        val source = operation.sourceRegion.normalized()
        val sourceMin = source.minCorner()
        val sourceMax = source.maxCorner()
        val queryBox = Box(
            sourceMin.x.toDouble(),
            sourceMin.y.toDouble(),
            sourceMin.z.toDouble(),
            sourceMax.x + 1.0,
            sourceMax.y + 1.25,
            sourceMax.z + 1.0,
        )
        val seen = linkedSetOf<java.util.UUID>()
        val dummyEntity = serverWorld.getEntitiesByClass(Entity::class.java, queryBox) { true }.firstOrNull()
        return VersionCompat.INSTANCE.worldGetOtherEntities(serverWorld, dummyEntity ?: return emptyList(), queryBox)
            .asSequence()
            .mapNotNull { it as? Entity }
            .map(::rootEntity)
            .filter { entity ->
                entity !is PlayerEntity &&
                    !VersionCompat.INSTANCE.entityIsRemoved(entity) &&
                    VersionCompat.INSTANCE.entityGetVehicle(entity) == null &&
                    seen.add(VersionCompat.INSTANCE.entityGetUuid(entity))
            }
            .flatMap { entity ->
                captureEntityTree(entity, parentEntityId = null).asSequence()
            }
            .toList()
    }

    fun apply(world: World, deletes: List<EntityCloneChange>) {
        val serverWorld = world as? ServerWorld ?: return
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
                    pos = net.minecraft.util.math.Vec3d(VersionCompat.INSTANCE.entityGetX(entity), VersionCompat.INSTANCE.entityGetY(entity), VersionCompat.INSTANCE.entityGetZ(entity)),
                    yaw = VersionCompat.INSTANCE.entityGetYaw(entity),
                    pitch = VersionCompat.INSTANCE.entityGetPitch(entity),
                ),
            )
            VersionCompat.INSTANCE.entityGetPassengerList(entity).forEach { passenger ->
                val p = passenger as? Entity ?: return@forEach
                if (p !is PlayerEntity && !VersionCompat.INSTANCE.entityIsRemoved(p)) {
                    addAll(captureEntityTree(p, parentEntityId = VersionCompat.INSTANCE.entityGetUuid(entity)))
                }
            }
        }
    }

    private fun capture(entity: Entity): NbtCompound? {
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

package axion.client.network

import axion.common.history.EntityCloneChange
import axion.common.operation.DeleteEntitiesOperation
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.storage.NbtWriteView
import net.minecraft.util.ErrorReporter
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
        return serverWorld.getOtherEntities(null, queryBox)
            .asSequence()
            .map(::rootEntity)
            .filter { entity ->
                entity !is PlayerEntity &&
                    !entity.isRemoved &&
                    entity.vehicle == null &&
                    seen.add(entity.uuid)
            }
            .flatMap { entity ->
                captureEntityTree(entity, parentEntityId = null).asSequence()
            }
            .toList()
    }

    fun apply(world: World, deletes: List<EntityCloneChange>) {
        val serverWorld = world as? ServerWorld ?: return
        deletes.forEach { delete ->
            serverWorld.getEntity(delete.entityId)?.remove(Entity.RemovalReason.DISCARDED)
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
                    entityId = entity.uuid,
                    parentEntityId = parentEntityId,
                    entityData = snapshot,
                    pos = net.minecraft.util.math.Vec3d(entity.x, entity.y, entity.z),
                    yaw = entity.yaw,
                    pitch = entity.pitch,
                ),
            )
            entity.passengerList.forEach { passenger ->
                if (passenger !is PlayerEntity && !passenger.isRemoved) {
                    addAll(captureEntityTree(passenger, parentEntityId = entity.uuid))
                }
            }
        }
    }

    private fun capture(entity: Entity): NbtCompound? {
        val output = NbtWriteView.create(ErrorReporter.EMPTY)
        if (!entity.saveSelfData(output)) {
            return null
        }
        return output.nbt
    }

    private fun rootEntity(entity: Entity): Entity {
        var current = entity
        while (current.vehicle != null) {
            current = current.vehicle!!
        }
        return current
    }
}

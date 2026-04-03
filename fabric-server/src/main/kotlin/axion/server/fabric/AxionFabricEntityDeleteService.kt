package axion.server.fabric

import axion.protocol.DeleteEntitiesRequest
import axion.protocol.IntVector3
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import java.util.UUID

object AxionFabricEntityDeleteService {
    fun apply(world: ServerWorld, operation: DeleteEntitiesRequest) {
        delete(world, operation)
    }

    fun apply(world: ServerWorld, deletes: List<FabricCommittedEntityClone>) {
        deletes.forEach { delete ->
            world.getEntity(delete.entityId)?.remove(Entity.RemovalReason.DISCARDED)
        }
    }

    fun delete(world: ServerWorld, operation: DeleteEntitiesRequest): List<FabricCommittedEntityClone> {
        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
        val queryBox = Box(
            sourceMin.x.toDouble(),
            sourceMin.y.toDouble(),
            sourceMin.z.toDouble(),
            sourceMax.x + 1.0,
            sourceMax.y + 1.25,
            sourceMax.z + 1.0,
        )
        val seen = linkedSetOf<UUID>()
        return world.getOtherEntities(null, queryBox)
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
            .also { deletes ->
                apply(world, deletes)
            }
    }

    private fun captureEntityTree(
        entity: Entity,
        parentEntityId: UUID?,
    ): List<FabricCommittedEntityClone> {
        val output = net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY)
        if (!entity.saveSelfData(output)) {
            return emptyList()
        }
        val snapshot = output.nbt
        return buildList {
            add(
                FabricCommittedEntityClone(
                    entityId = entity.uuid,
                    parentEntityId = parentEntityId,
                    entityData = snapshot.toString(),
                    x = entity.x,
                    y = entity.y,
                    z = entity.z,
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

    private fun rootEntity(entity: Entity): Entity {
        var current = entity
        while (current.vehicle != null) {
            current = current.vehicle!!
        }
        return current
    }

    private fun minVector(a: IntVector3, b: IntVector3): IntVector3 {
        return IntVector3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
    }

    private fun maxVector(a: IntVector3, b: IntVector3): IntVector3 {
        return IntVector3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
    }
}

package axion.server.paper

import axion.protocol.DeleteEntitiesRequest
import net.minecraft.nbt.CompoundTag
import org.bukkit.World
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox

object PaperEntityDeleteService {
    fun delete(world: World, operation: DeleteEntitiesRequest): List<CommittedEntityClone> {
        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
        val queryBox = BoundingBox(
            sourceMin.x.toDouble(),
            sourceMin.y.toDouble(),
            sourceMin.z.toDouble(),
            sourceMax.x + 1.0,
            sourceMax.y + 1.25,
            sourceMax.z + 1.0,
        )
        val seen = linkedSetOf<java.util.UUID>()
        val deletes = world.getNearbyEntities(queryBox)
            .asSequence()
            .map(::rootEntity)
            .filter { entity ->
                entity !is Player &&
                    entity.vehicle == null &&
                    entity.isValid &&
                    seen.add(entity.uniqueId)
            }
            .flatMap { entity ->
                captureEntityTree(entity, parentEntityId = null).asSequence()
            }
            .toList()
        apply(world, deletes)
        return deletes
    }

    fun apply(world: World, deletes: List<CommittedEntityClone>) {
        deletes.forEach { delete ->
            world.entities.firstOrNull { it.uniqueId == delete.entityId }?.remove()
        }
    }

    private fun captureEntityTree(
        entity: Entity,
        parentEntityId: java.util.UUID?,
    ): List<CommittedEntityClone> {
        val snapshot = capture(entity) ?: return emptyList()
        return buildList {
            add(
                CommittedEntityClone(
                    entityId = entity.uniqueId,
                    parentEntityId = parentEntityId,
                    entityData = snapshot.toString(),
                    spawnLocation = entity.location.clone(),
                ),
            )
            entity.passengers.forEach { passenger ->
                if (passenger !is Player && passenger.isValid) {
                    addAll(captureEntityTree(passenger, parentEntityId = entity.uniqueId))
                }
            }
        }
    }

    private fun capture(entity: Entity): CompoundTag? {
        val tag = CompoundTag()
        val handle = (entity as org.bukkit.craftbukkit.entity.CraftEntity).handle
        if (!saveEntityAsPassenger(handle, tag)) {
            return null
        }
        return tag
    }

    private fun saveEntityAsPassenger(entity: net.minecraft.world.entity.Entity, tag: CompoundTag): Boolean {
        val compoundMethod = entity.javaClass.methods.firstOrNull { method ->
            method.name == "saveAsPassenger" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == CompoundTag::class.java
        }
        if (compoundMethod != null) {
            return compoundMethod.invoke(entity, tag) as Boolean
        }

        val outputClass = Class.forName("net.minecraft.world.level.storage.TagValueOutput")
        val reporter = Class.forName("net.minecraft.util.ProblemReporter").getField("DISCARDING").get(null)
        val output = outputClass.getMethods().first { method ->
            method.name == "createWithoutContext" && method.parameterTypes.size == 1
        }.invoke(null, reporter)
        val saved = entity.javaClass.methods.first { method ->
            method.name == "saveAsPassenger" && method.parameterTypes.size == 1
        }.invoke(entity, output) as Boolean
        if (saved) {
            tag.merge(output.javaClass.getMethod("buildResult").invoke(output) as CompoundTag)
        }
        return saved
    }

    private fun rootEntity(entity: Entity): Entity {
        var current = entity
        while (current.vehicle != null) {
            current = current.vehicle!!
        }
        return current
    }

    private fun minVector(a: axion.protocol.IntVector3, b: axion.protocol.IntVector3): axion.protocol.IntVector3 {
        return axion.protocol.IntVector3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
    }

    private fun maxVector(a: axion.protocol.IntVector3, b: axion.protocol.IntVector3): axion.protocol.IntVector3 {
        return axion.protocol.IntVector3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
    }
}

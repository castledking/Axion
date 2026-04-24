package axion.server.paper

import axion.protocol.CloneEntitiesRequest
import axion.protocol.PlacementMirrorAxisPayload
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.level.storage.TagValueOutput
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

object PaperEntityCloneService {
    fun clone(world: World, operation: CloneEntitiesRequest): List<CommittedEntityClone> {
        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
        val sizeX = sourceMax.x - sourceMin.x + 1.0
        val sizeY = sourceMax.y - sourceMin.y + 1.0
        val sizeZ = sourceMax.z - sourceMin.z + 1.0
        val level = (world as CraftWorld).handle
        val seen = linkedSetOf<UUID>()
        return operation.entityUuids
            .asSequence()
            .mapNotNull { uuid -> world.entities.firstOrNull { it.uniqueId == uuid } }
            .map(::rootEntity)
            .filter { entity ->
                entity !is Player &&
                    entity.vehicle == null &&
                    entity.isValid &&
                    seen.add(entity.uniqueId)
            }
            .flatMap { entity ->
                val clones = planEntityTree(
                    entity = entity,
                    operation = operation,
                    sourceMinX = sourceMin.x,
                    sourceMinY = sourceMin.y,
                    sourceMinZ = sourceMin.z,
                    sizeX = sizeX,
                    sizeY = sizeY,
                    sizeZ = sizeZ,
                    parentCloneId = null,
                )
                val spawned = linkedMapOf<UUID, net.minecraft.world.entity.Entity>()
                clones.forEach { clone ->
                    spawnClone(level, net.minecraft.nbt.TagParser.parseCompoundFully(clone.entityData), clone.spawnLocation, clone.entityId)
                        ?.let { spawned[clone.entityId] = it }
                }
                clones.forEach { clone ->
                    val parentId = clone.parentEntityId ?: return@forEach
                    val child = spawned[clone.entityId] ?: return@forEach
                    val parent = spawned[parentId] ?: return@forEach
                    startRidingCompat(child, parent)
                }
                spawned.values
                    .filter { it.vehicle == null }
                    .forEach(::refreshPassengerPositions)
                clones.asSequence()
            }
            .toList()
    }

    fun remove(world: World, clones: List<CommittedEntityClone>) {
        clones.forEach { clone ->
            world.entities.firstOrNull { it.uniqueId == clone.entityId }?.remove()
        }
    }

    fun respawn(world: World, clones: List<CommittedEntityClone>) {
        val level = (world as CraftWorld).handle
        val spawned = linkedMapOf<UUID, net.minecraft.world.entity.Entity>()
        clones.forEach { clone ->
            spawnClone(level, net.minecraft.nbt.TagParser.parseCompoundFully(clone.entityData), clone.spawnLocation, clone.entityId)
                ?.let { spawned[clone.entityId] = it }
        }
        clones.forEach { clone ->
            val parentId = clone.parentEntityId ?: return@forEach
            val child = spawned[clone.entityId] ?: return@forEach
            val parent = spawned[parentId] ?: return@forEach
            startRidingCompat(child, parent)
        }
        spawned.values
            .filter { it.vehicle == null }
            .forEach(::refreshPassengerPositions)
    }

    private fun capture(entity: Entity): CompoundTag? {
        val output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING)
        if (!(entity as org.bukkit.craftbukkit.entity.CraftEntity).handle.saveAsPassenger(output)) {
            return null
        }
        val tag = output.buildResult()
        val passengers = ListTag()
        entity.passengers.forEach { passenger ->
            capture(passenger)?.let(passengers::add)
        }
        if (!passengers.isEmpty()) {
            tag.put("Passengers", passengers)
        }
        return tag
    }

    private fun spawnClone(
        level: net.minecraft.server.level.ServerLevel,
        tag: CompoundTag,
        location: Location,
        entityId: UUID,
    ): net.minecraft.world.entity.Entity? {
        stripUuids(tag)
        val entity = EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.COMMAND) { entity ->
            entity.setUUID(entityId)
            entity.snapTo(location.x, location.y, location.z, location.yaw, location.pitch)
            entity
        } ?: return null
        level.tryAddFreshEntityWithPassengers(entity)
        return entity
    }

    private fun stripUuids(tag: CompoundTag) {
        tag.remove("UUID")
        tag.getList("Passengers").ifPresent { passengers ->
            passengers.forEach { nested ->
                val compound = nested.asCompound().orElse(null) ?: return@forEach
                stripUuids(compound)
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

    private fun startRidingCompat(
        child: net.minecraft.world.entity.Entity,
        parent: net.minecraft.world.entity.Entity,
    ) {
        val startRidingMethods = child.javaClass.methods.filter { it.name == "startRiding" }
        startRidingMethods.firstOrNull { it.parameterCount == 3 }?.invoke(child, parent, true, true)?.let { return }
        startRidingMethods.firstOrNull { it.parameterCount == 2 }?.invoke(child, parent, true)?.let { return }
        startRidingMethods.firstOrNull { it.parameterCount == 1 }?.invoke(child, parent)
    }

    private fun planEntityTree(
        entity: Entity,
        operation: CloneEntitiesRequest,
        sourceMinX: Int,
        sourceMinY: Int,
        sourceMinZ: Int,
        sizeX: Double,
        sizeY: Double,
        sizeZ: Double,
        parentCloneId: UUID?,
    ): List<CommittedEntityClone> {
        val snapshot = capture(entity) ?: return emptyList()
        stripUuids(snapshot)
        val cloneId = UUID.randomUUID()
        val target = transformLocation(entity.location, sourceMinX, sourceMinY, sourceMinZ, sizeX, sizeY, sizeZ, operation)
        return buildList {
            add(
                CommittedEntityClone(
                    entityId = cloneId,
                    parentEntityId = parentCloneId,
                    entityData = snapshot.toString(),
                    spawnLocation = target,
                ),
            )
            entity.passengers.forEach { passenger ->
                if (passenger !is Player && passenger.isValid) {
                    addAll(
                        planEntityTree(
                            entity = passenger,
                            operation = operation,
                            sourceMinX = sourceMinX,
                            sourceMinY = sourceMinY,
                            sourceMinZ = sourceMinZ,
                            sizeX = sizeX,
                            sizeY = sizeY,
                            sizeZ = sizeZ,
                            parentCloneId = cloneId,
                        ),
                    )
                }
            }
        }
    }

    private fun refreshPassengerPositions(entity: net.minecraft.world.entity.Entity) {
        entity.passengers.forEach { passenger ->
            entity.positionRider(passenger)
            refreshPassengerPositions(passenger)
        }
    }

    private fun transformLocation(
        location: Location,
        sourceMinX: Int,
        sourceMinY: Int,
        sourceMinZ: Int,
        sizeX: Double,
        sizeY: Double,
        sizeZ: Double,
        operation: CloneEntitiesRequest,
    ): Location {
        val relative = Vector(
            location.x - sourceMinX,
            location.y - sourceMinY,
            location.z - sourceMinZ,
        )
        val mirrored = when (operation.mirrorAxis) {
            PlacementMirrorAxisPayload.NONE -> relative
            PlacementMirrorAxisPayload.X -> Vector(sizeX - relative.x, relative.y, relative.z)
            PlacementMirrorAxisPayload.Y -> Vector(relative.x, sizeY - relative.y, relative.z)
            PlacementMirrorAxisPayload.Z -> Vector(relative.x, relative.y, sizeZ - relative.z)
        }
        val rotatedPosition = rotatePosition(mirrored, sizeX, sizeZ, operation.rotationQuarterTurns)
        val transformedDirection = rotateDirection(mirrorDirection(location.direction, operation.mirrorAxis), operation.rotationQuarterTurns)
        return Location(
            location.world,
            operation.destinationOrigin.x + rotatedPosition.x,
            operation.destinationOrigin.y + rotatedPosition.y,
            operation.destinationOrigin.z + rotatedPosition.z,
            directionToYaw(transformedDirection),
            directionToPitch(transformedDirection),
        )
    }

    private fun rotatePosition(position: Vector, sizeX: Double, sizeZ: Double, turns: Int): Vector {
        return when (Math.floorMod(turns, 4)) {
            0 -> position
            1 -> Vector(sizeZ - position.z, position.y, position.x)
            2 -> Vector(sizeX - position.x, position.y, sizeZ - position.z)
            else -> Vector(position.z, position.y, sizeX - position.x)
        }
    }

    private fun mirrorDirection(direction: Vector, axis: PlacementMirrorAxisPayload): Vector {
        return when (axis) {
            PlacementMirrorAxisPayload.NONE -> direction.clone()
            PlacementMirrorAxisPayload.X -> Vector(-direction.x, direction.y, direction.z)
            PlacementMirrorAxisPayload.Y -> Vector(direction.x, -direction.y, direction.z)
            PlacementMirrorAxisPayload.Z -> Vector(direction.x, direction.y, -direction.z)
        }
    }

    private fun rotateDirection(direction: Vector, turns: Int): Vector {
        return when (Math.floorMod(turns, 4)) {
            0 -> direction
            1 -> Vector(-direction.z, direction.y, direction.x)
            2 -> Vector(-direction.x, direction.y, -direction.z)
            else -> Vector(direction.z, direction.y, -direction.x)
        }
    }

    private fun directionToYaw(direction: Vector): Float {
        return Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
    }

    private fun directionToPitch(direction: Vector): Float {
        val horizontal = sqrt(direction.x * direction.x + direction.z * direction.z)
        return Math.toDegrees(-atan2(direction.y, horizontal)).toFloat()
    }

    private fun minVector(a: axion.protocol.IntVector3, b: axion.protocol.IntVector3): axion.protocol.IntVector3 {
        return axion.protocol.IntVector3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
    }

    private fun maxVector(a: axion.protocol.IntVector3, b: axion.protocol.IntVector3): axion.protocol.IntVector3 {
        return axion.protocol.IntVector3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
    }
}

package axion.server.fabric

import axion.protocol.CloneEntitiesRequest
import axion.protocol.IntVector3
import axion.protocol.PlacementMirrorAxisPayload
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.StringNbtReader
import net.minecraft.server.world.ServerWorld
import net.minecraft.storage.NbtWriteView
import net.minecraft.util.ErrorReporter
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

object AxionFabricEntityCloneService {
    fun apply(world: ServerWorld, operation: CloneEntitiesRequest) {
        clone(world, operation)
    }

    fun clone(world: ServerWorld, operation: CloneEntitiesRequest): List<FabricCommittedEntityClone> {
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
        val clones = world.getOtherEntities(null, queryBox)
            .asSequence()
            .map(::rootEntity)
            .filter { entity ->
                entity !is PlayerEntity &&
                    !entity.isRemoved &&
                    entity.vehicle == null &&
                    seen.add(entity.uuid)
            }
            .flatMap { entity ->
                cloneTree(
                    entity = entity,
                    sourceMin = sourceMin,
                    sourceMax = sourceMax,
                    operation = operation,
                    parentCloneId = null,
                ).asSequence()
            }
            .toList()

        val spawned = linkedMapOf<UUID, Entity>()
        clones.forEach { clone ->
            spawnClone(world, clone)?.let { spawned[clone.entityId] = it }
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
        return clones
    }

    fun remove(world: ServerWorld, clones: List<FabricCommittedEntityClone>) {
        clones.forEach { clone ->
            world.getEntity(clone.entityId)?.remove(Entity.RemovalReason.DISCARDED)
        }
    }

    fun respawn(world: ServerWorld, clones: List<FabricCommittedEntityClone>) {
        val spawned = linkedMapOf<UUID, Entity>()
        clones.forEach { clone ->
            spawnClone(world, clone)?.let { spawned[clone.entityId] = it }
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

    private fun cloneTree(
        entity: Entity,
        sourceMin: IntVector3,
        sourceMax: IntVector3,
        operation: CloneEntitiesRequest,
        parentCloneId: UUID?,
    ): List<FabricCommittedEntityClone> {
        val snapshot = capture(entity) ?: return emptyList()
        stripUuids(snapshot)
        val target = transformEntity(
            position = Vec3d(entity.x, entity.y, entity.z),
            direction = directionFromAngles(entity.yaw, entity.pitch),
            sourceMin = sourceMin,
            sourceMax = sourceMax,
            destinationOrigin = operation.destinationOrigin,
            mirrorAxis = operation.mirrorAxis,
            rotationQuarterTurns = operation.rotationQuarterTurns,
        )
        val cloneId = UUID.randomUUID()
        return buildList {
            add(
                FabricCommittedEntityClone(
                    entityId = cloneId,
                    parentEntityId = parentCloneId,
                    entityData = snapshot.toString(),
                    x = target.position.x,
                    y = target.position.y,
                    z = target.position.z,
                    yaw = target.yaw,
                    pitch = target.pitch,
                ),
            )
            entity.passengerList.forEach { passenger ->
                if (passenger !is PlayerEntity && !passenger.isRemoved) {
                    addAll(
                        cloneTree(
                            entity = passenger,
                            sourceMin = sourceMin,
                            sourceMax = sourceMax,
                            operation = operation,
                            parentCloneId = cloneId,
                        ),
                    )
                }
            }
        }
    }

    private fun spawnClone(world: ServerWorld, clone: FabricCommittedEntityClone): Entity? {
        val tag = StringNbtReader.readCompound(clone.entityData)
        stripUuids(tag)
        val entity = EntityType.loadEntityWithPassengers(tag, world, SpawnReason.COMMAND) { spawned: Entity ->
            spawned.setUuid(clone.entityId)
            spawned.refreshPositionAndAngles(clone.x, clone.y, clone.z, clone.yaw, clone.pitch)
            spawned
        } ?: return null
        world.spawnNewEntityAndPassengers(entity)
        return entity
    }

    private fun capture(entity: Entity): NbtCompound? {
        val output = NbtWriteView.create(ErrorReporter.EMPTY)
        if (!entity.saveSelfData(output)) {
            return null
        }
        val tag = output.nbt
        val passengers = NbtList()
        entity.passengerList.forEach { passenger ->
            capture(passenger)?.let(passengers::add)
        }
        if (!passengers.isEmpty()) {
            tag.put("Passengers", passengers)
        }
        return tag
    }

    private fun stripUuids(tag: NbtCompound) {
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

    private fun refreshPassengerPositions(entity: Entity) {
        entity.passengerList.forEach { passenger ->
            entity.updatePassengerPosition(passenger)
            refreshPassengerPositions(passenger)
        }
    }

    private fun startRidingCompat(child: Entity, parent: Entity) {
        val startRidingMethods = child.javaClass.methods.filter { it.name == "startRiding" }
        startRidingMethods.firstOrNull { it.parameterCount == 3 }?.invoke(child, parent, true, true)?.let { return }
        startRidingMethods.firstOrNull { it.parameterCount == 2 }?.invoke(child, parent, true)?.let { return }
        startRidingMethods.firstOrNull { it.parameterCount == 1 }?.invoke(child, parent)
    }

    private fun transformEntity(
        position: Vec3d,
        direction: Vec3d,
        sourceMin: IntVector3,
        sourceMax: IntVector3,
        destinationOrigin: IntVector3,
        mirrorAxis: PlacementMirrorAxisPayload,
        rotationQuarterTurns: Int,
    ): EntityTarget {
        val sizeX = sourceMax.x - sourceMin.x + 1.0
        val sizeZ = sourceMax.z - sourceMin.z + 1.0
        val relative = position.subtract(sourceMin.x.toDouble(), sourceMin.y.toDouble(), sourceMin.z.toDouble())
        val mirrored = when (mirrorAxis) {
            PlacementMirrorAxisPayload.NONE -> relative
            PlacementMirrorAxisPayload.X -> Vec3d(sizeX - relative.x, relative.y, relative.z)
            PlacementMirrorAxisPayload.Z -> Vec3d(relative.x, relative.y, sizeZ - relative.z)
        }
        val rotatedPosition = rotatePosition(mirrored, sizeX, sizeZ, rotationQuarterTurns)
        val transformedDirection = rotateDirection(mirrorDirection(direction, mirrorAxis), rotationQuarterTurns)
        return EntityTarget(
            position = rotatedPosition.add(destinationOrigin.x.toDouble(), destinationOrigin.y.toDouble(), destinationOrigin.z.toDouble()),
            yaw = directionToYaw(transformedDirection),
            pitch = directionToPitch(transformedDirection),
        )
    }

    private fun rotatePosition(position: Vec3d, sizeX: Double, sizeZ: Double, turns: Int): Vec3d {
        return when (Math.floorMod(turns, 4)) {
            0 -> position
            1 -> Vec3d(sizeZ - position.z, position.y, position.x)
            2 -> Vec3d(sizeX - position.x, position.y, sizeZ - position.z)
            else -> Vec3d(position.z, position.y, sizeX - position.x)
        }
    }

    private fun mirrorDirection(direction: Vec3d, axis: PlacementMirrorAxisPayload): Vec3d {
        return when (axis) {
            PlacementMirrorAxisPayload.NONE -> direction
            PlacementMirrorAxisPayload.X -> Vec3d(-direction.x, direction.y, direction.z)
            PlacementMirrorAxisPayload.Z -> Vec3d(direction.x, direction.y, -direction.z)
        }
    }

    private fun rotateDirection(direction: Vec3d, turns: Int): Vec3d {
        return when (Math.floorMod(turns, 4)) {
            0 -> direction
            1 -> Vec3d(-direction.z, direction.y, direction.x)
            2 -> Vec3d(-direction.x, direction.y, -direction.z)
            else -> Vec3d(direction.z, direction.y, -direction.x)
        }
    }

    private fun directionToYaw(direction: Vec3d): Float {
        return Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
    }

    private fun directionToPitch(direction: Vec3d): Float {
        val horizontal = sqrt(direction.x * direction.x + direction.z * direction.z)
        return Math.toDegrees(-atan2(direction.y, horizontal)).toFloat()
    }

    private fun directionFromAngles(yaw: Float, pitch: Float): Vec3d {
        val yawRadians = Math.toRadians(yaw.toDouble())
        val pitchRadians = Math.toRadians(pitch.toDouble())
        val cosPitch = kotlin.math.cos(pitchRadians)
        return Vec3d(
            -kotlin.math.sin(yawRadians) * cosPitch,
            -kotlin.math.sin(pitchRadians),
            kotlin.math.cos(yawRadians) * cosPitch,
        )
    }

    private fun minVector(a: IntVector3, b: IntVector3): IntVector3 {
        return IntVector3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
    }

    private fun maxVector(a: IntVector3, b: IntVector3): IntVector3 {
        return IntVector3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
    }

    private data class EntityTarget(
        val position: Vec3d,
        val yaw: Float,
        val pitch: Float,
    )
}

package axion.client.network

import axion.common.history.EntityCloneChange
import axion.common.operation.CloneEntitiesOperation
import axion.common.operation.EntityMoveMirrorAxis
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LoadedEntityProcessor
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.server.world.ServerWorld
import net.minecraft.storage.NbtWriteView
import net.minecraft.util.ErrorReporter
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

object LocalEntityCloneService {
    fun plan(world: World, operation: CloneEntitiesOperation): List<EntityCloneChange> {
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
        val seen = linkedSetOf<UUID>()
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
                planEntityTree(
                    entity = entity,
                    sourceMin = sourceMin,
                    sourceMax = sourceMax,
                    operation = operation,
                    parentCloneId = null,
                ).asSequence()
            }
            .toList()
    }

    fun apply(world: World, clones: List<EntityCloneChange>) {
        val serverWorld = world as? ServerWorld ?: return
        val spawned = linkedMapOf<UUID, Entity>()
        clones.forEach { clone ->
            spawnClone(serverWorld, clone)?.let { spawned[clone.entityId] = it }
        }
        clones.forEach { clone ->
            val parentId = clone.parentEntityId ?: return@forEach
            val child = spawned[clone.entityId] ?: return@forEach
            val parent = spawned[parentId] ?: return@forEach
            child.startRiding(parent, true, true)
        }
        spawned.values
            .filter { it.vehicle == null }
            .forEach(::refreshPassengerPositions)
    }

    fun remove(world: World, clones: List<EntityCloneChange>) {
        val serverWorld = world as? ServerWorld ?: return
        clones.forEach { clone ->
            serverWorld.getEntity(clone.entityId)?.remove(Entity.RemovalReason.DISCARDED)
        }
    }

    private fun spawnClone(world: ServerWorld, clone: EntityCloneChange): Entity? {
        val tag = clone.entityData.copy()
        stripUuids(tag)
        val entity = EntityType.loadEntityWithPassengers(tag, world, SpawnReason.COMMAND, LoadedEntityProcessor { entity ->
            entity.setUuid(clone.entityId)
            entity.refreshPositionAndAngles(clone.pos.x, clone.pos.y, clone.pos.z, clone.yaw, clone.pitch)
            entity
        }) ?: return null
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
        entity.getPassengerList().forEach { passenger ->
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

    private fun planEntityTree(
        entity: Entity,
        sourceMin: BlockPos,
        sourceMax: BlockPos,
        operation: CloneEntitiesOperation,
        parentCloneId: UUID?,
    ): List<EntityCloneChange> {
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
                EntityCloneChange(
                    entityId = cloneId,
                    parentEntityId = parentCloneId,
                    entityData = snapshot,
                    pos = target.position,
                    yaw = target.yaw,
                    pitch = target.pitch,
                ),
            )
            entity.getPassengerList().forEach { passenger ->
                if (passenger !is PlayerEntity && !passenger.isRemoved) {
                    addAll(
                        planEntityTree(
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

    private fun refreshPassengerPositions(entity: Entity) {
        entity.getPassengerList().forEach { passenger ->
            entity.updatePassengerPosition(passenger)
            refreshPassengerPositions(passenger)
        }
    }

    private fun transformEntity(
        position: Vec3d,
        direction: Vec3d,
        sourceMin: BlockPos,
        sourceMax: BlockPos,
        destinationOrigin: BlockPos,
        mirrorAxis: EntityMoveMirrorAxis,
        rotationQuarterTurns: Int,
    ): EntityTarget {
        val sizeX = sourceMax.x - sourceMin.x + 1.0
        val sizeZ = sourceMax.z - sourceMin.z + 1.0
        val relative = position.subtract(sourceMin.x.toDouble(), sourceMin.y.toDouble(), sourceMin.z.toDouble())
        val mirrored = when (mirrorAxis) {
            EntityMoveMirrorAxis.NONE -> relative
            EntityMoveMirrorAxis.X -> Vec3d(sizeX - relative.x, relative.y, relative.z)
            EntityMoveMirrorAxis.Z -> Vec3d(relative.x, relative.y, sizeZ - relative.z)
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

    private fun mirrorDirection(direction: Vec3d, axis: EntityMoveMirrorAxis): Vec3d {
        return when (axis) {
            EntityMoveMirrorAxis.NONE -> direction
            EntityMoveMirrorAxis.X -> Vec3d(-direction.x, direction.y, direction.z)
            EntityMoveMirrorAxis.Z -> Vec3d(direction.x, direction.y, -direction.z)
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

    private data class EntityTarget(
        val position: Vec3d,
        val yaw: Float,
        val pitch: Float,
    )
}

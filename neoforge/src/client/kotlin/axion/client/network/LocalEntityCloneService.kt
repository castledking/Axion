package axion.client.network

import axion.client.compat.VersionCompatImpl
import axion.common.compat.VersionCompat
import axion.common.history.EntityCloneChange
import axion.common.operation.CloneEntitiesOperation
import axion.common.operation.EntityMoveMirrorAxis
import axion.protocol.IntVector3
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.Level
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

object LocalEntityCloneService {
    fun plan(world: Level, operation: CloneEntitiesOperation): List<EntityCloneChange> {
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
        val entityMatcher = operation.entitySelection.matcher(
            IntVector3(sourceMin.x, sourceMin.y, sourceMin.z),
            IntVector3(sourceMax.x, sourceMax.y, sourceMax.z),
        )
        val seen = linkedSetOf<UUID>()
        return serverWorld.getEntitiesByClass(Entity::class.java, queryBox) { entity ->
            entity !is Player &&
                !VersionCompat.INSTANCE.entityIsRemoved(entity) &&
                entityMatcher.containsFeet(
                    VersionCompat.INSTANCE.entityGetX(entity),
                    VersionCompat.INSTANCE.entityGetY(entity),
                    VersionCompat.INSTANCE.entityGetZ(entity),
                )
        }
            .asSequence()
            .map(::rootEntity)
            .filter { entity ->
                entity !is Player &&
                    !VersionCompat.INSTANCE.entityIsRemoved(entity) &&
                    VersionCompat.INSTANCE.entityGetVehicle(entity) == null &&
                    seen.add(VersionCompat.INSTANCE.entityGetUuid(entity))
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

    fun apply(world: Level, clones: List<EntityCloneChange>) {
        val serverWorld = world as? ServerLevel ?: return
        val spawned = linkedMapOf<UUID, Entity>()
        clones.forEach { clone ->
            spawnClone(serverWorld, clone)?.let { spawned[clone.entityId] = it }
        }
        clones.forEach { clone ->
            val parentId = clone.parentEntityId ?: return@forEach
            val child = spawned[clone.entityId] ?: return@forEach
            val parent = spawned[parentId] ?: return@forEach
            startRidingCompat(child, parent)
        }
        spawned.values
            .filter { VersionCompat.INSTANCE.entityGetVehicle(it) == null }
            .forEach(::refreshPassengerPositions)
    }

    fun remove(world: Level, clones: List<EntityCloneChange>) {
        val serverWorld = world as? ServerLevel ?: return
        clones.forEach { clone ->
            serverWorld.getEntity(clone.entityId)?.discard()
        }
    }

    private fun spawnClone(world: ServerLevel, clone: EntityCloneChange): Entity? {
        val tag = clone.entityData.copy()
        stripUuids(tag)
        val entity = VersionCompat.INSTANCE.entityTypeLoadEntityWithPassengers(tag, world, EntitySpawnReason.COMMAND) { entity ->
            VersionCompat.INSTANCE.entitySetUuid(entity, clone.entityId)
            entity
        } ?: return null
        val cloneEntity = entity as? Entity ?: return null
        LocalEntityPositioning.apply(cloneEntity, clone.pos, clone.yRot, clone.xRot)
        VersionCompat.INSTANCE.worldSpawnNewEntityAndPassengers(world, cloneEntity)
        return cloneEntity
    }

    private fun capture(entity: Entity): CompoundTag? {
        val tag = VersionCompatImpl.captureEntityData(entity) ?: return null
        val passengers = ListTag()
        VersionCompat.INSTANCE.entityGetPassengerList(entity).forEach { passenger ->
            val p = passenger as? Entity ?: return@forEach
            capture(p)?.let(passengers::add)
        }
        if (!passengers.isEmpty()) {
            tag.put("Passengers", passengers)
        }
        return tag
    }

    private fun stripUuids(tag: CompoundTag) {
        tag.remove("UUID")
        val passengers = tag.get("Passengers") as? ListTag ?: return
        passengers.forEach { nested ->
            val compound = nested as? CompoundTag ?: return@forEach
            stripUuids(compound)
        }
    }

    private fun rootEntity(entity: Entity): Entity {
        var current = entity
        while (VersionCompat.INSTANCE.entityGetVehicle(current) != null) {
            current = VersionCompat.INSTANCE.entityGetVehicle(current) as Entity
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
            position = Vec3(VersionCompat.INSTANCE.entityGetX(entity), VersionCompat.INSTANCE.entityGetY(entity), VersionCompat.INSTANCE.entityGetZ(entity)),
            direction = directionFromAngles(VersionCompat.INSTANCE.entityGetYaw(entity), VersionCompat.INSTANCE.entityGetPitch(entity)),
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
                    yaw = target.yRot,
                    pitch = target.xRot,
                ),
            )
            VersionCompat.INSTANCE.entityGetPassengerList(entity).forEach { passenger ->
                val p = passenger as? Entity ?: return@forEach
                if (p !is Player && !VersionCompat.INSTANCE.entityIsRemoved(p)) {
                    addAll(
                        planEntityTree(
                            entity = p,
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
        VersionCompat.INSTANCE.entityGetPassengerList(entity).forEach { passenger ->
            VersionCompat.INSTANCE.entityUpdatePassengerPosition(entity, passenger)
            refreshPassengerPositions(passenger as Entity)
        }
    }

    private fun startRidingCompat(child: Entity, parent: Entity) {
        val startRidingMethods = child.javaClass.methods.filter { it.name == "startRiding" }
        startRidingMethods.firstOrNull { it.parameterCount == 3 }?.invoke(child, parent, true, true)?.let { return }
        startRidingMethods.firstOrNull { it.parameterCount == 2 }?.invoke(child, parent, true)?.let { return }
        startRidingMethods.firstOrNull { it.parameterCount == 1 }?.invoke(child, parent)
    }

    private fun transformEntity(
        position: Vec3,
        direction: Vec3,
        sourceMin: BlockPos,
        sourceMax: BlockPos,
        destinationOrigin: BlockPos,
        mirrorAxis: EntityMoveMirrorAxis,
        rotationQuarterTurns: Int,
    ): EntityTarget {
        val sizeX = sourceMax.x - sourceMin.x + 1.0
        val sizeZ = sourceMax.z - sourceMin.z + 1.0
        val relative = position.subtract(sourceMin.x.toDouble(), sourceMin.y.toDouble(), sourceMin.z.toDouble())
        val sizeY = sourceMax.y - sourceMin.y + 1.0
        val mirrored = when (mirrorAxis) {
            EntityMoveMirrorAxis.NONE -> relative
            EntityMoveMirrorAxis.X -> Vec3(sizeX - relative.x, relative.y, relative.z)
            EntityMoveMirrorAxis.Y -> Vec3(relative.x, sizeY - relative.y, relative.z)
            EntityMoveMirrorAxis.Z -> Vec3(relative.x, relative.y, sizeZ - relative.z)
        }
        val rotatedPosition = rotatePosition(mirrored, sizeX, sizeZ, rotationQuarterTurns)
        val transformedDirection = rotateDirection(mirrorDirection(direction, mirrorAxis), rotationQuarterTurns)
        return EntityTarget(
            position = rotatedPosition.add(destinationOrigin.x.toDouble(), destinationOrigin.y.toDouble(), destinationOrigin.z.toDouble()),
            yaw = directionToYaw(transformedDirection),
            pitch = directionToPitch(transformedDirection),
        )
    }

    private fun rotatePosition(position: Vec3, sizeX: Double, sizeZ: Double, turns: Int): Vec3 {
        return when (Math.floorMod(turns, 4)) {
            0 -> position
            1 -> Vec3(sizeZ - position.z, position.y, position.x)
            2 -> Vec3(sizeX - position.x, position.y, sizeZ - position.z)
            else -> Vec3(position.z, position.y, sizeX - position.x)
        }
    }

    private fun mirrorDirection(direction: Vec3, axis: EntityMoveMirrorAxis): Vec3 {
        return when (axis) {
            EntityMoveMirrorAxis.NONE -> direction
            EntityMoveMirrorAxis.X -> Vec3(-direction.x, direction.y, direction.z)
            EntityMoveMirrorAxis.Y -> Vec3(direction.x, -direction.y, direction.z)
            EntityMoveMirrorAxis.Z -> Vec3(direction.x, direction.y, -direction.z)
        }
    }

    private fun rotateDirection(direction: Vec3, turns: Int): Vec3 {
        return when (Math.floorMod(turns, 4)) {
            0 -> direction
            1 -> Vec3(-direction.z, direction.y, direction.x)
            2 -> Vec3(-direction.x, direction.y, -direction.z)
            else -> Vec3(direction.z, direction.y, -direction.x)
        }
    }

    private fun directionToYaw(direction: Vec3): Float {
        return Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
    }

    private fun directionToPitch(direction: Vec3): Float {
        val horizontal = sqrt(direction.x * direction.x + direction.z * direction.z)
        return Math.toDegrees(-atan2(direction.y, horizontal)).toFloat()
    }

    private fun directionFromAngles(yaw: Float, pitch: Float): Vec3 {
        val yawRadians = Math.toRadians(yaw.toDouble())
        val pitchRadians = Math.toRadians(pitch.toDouble())
        val cosPitch = kotlin.math.cos(pitchRadians)
        return Vec3(
            -kotlin.math.sin(yawRadians) * cosPitch,
            -kotlin.math.sin(pitchRadians),
            kotlin.math.cos(yawRadians) * cosPitch,
        )
    }

    private data class EntityTarget(
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
    )
}

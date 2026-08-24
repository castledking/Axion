package axion.client.network

import axion.common.compat.VersionCompat
import axion.common.operation.EntityMoveMirrorAxis
import axion.common.operation.MoveEntitiesOperation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.Level
import axion.protocol.IntVector3
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

object LocalEntityMoveService {
    fun plan(world: Level, operation: MoveEntitiesOperation): List<EntityMovePlan> {
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
            .map { entity ->
                val currentPos = Vec3(VersionCompat.INSTANCE.entityGetX(entity), VersionCompat.INSTANCE.entityGetY(entity), VersionCompat.INSTANCE.entityGetZ(entity))
                val target = transformEntity(currentPos, directionFromAngles(VersionCompat.INSTANCE.entityGetYaw(entity), VersionCompat.INSTANCE.entityGetPitch(entity)), sourceMin, sourceMax, operation)
                EntityMovePlan(
                    entityId = VersionCompat.INSTANCE.entityGetUuid(entity),
                    fromPos = currentPos,
                    toPos = target.position,
                    fromYaw = VersionCompat.INSTANCE.entityGetYaw(entity),
                    fromPitch = VersionCompat.INSTANCE.entityGetPitch(entity),
                    toYaw = target.yaw,
                    toPitch = target.pitch,
                )
            }
            .toList()
    }

    fun apply(world: Level, moves: List<EntityMovePlan>, reverse: Boolean = false) {
        val serverWorld = world as? ServerLevel ?: return
        moves.forEach { move ->
            val entity = serverWorld.getEntity(move.entityId) ?: return@forEach
            if (entity is Player) {
                return@forEach
            }
            val targetPos = if (reverse) move.fromPos else move.toPos
            val yaw = if (reverse) move.fromYaw else move.toYaw
            val pitch = if (reverse) move.fromPitch else move.toPitch
            LocalEntityPositioning.apply(entity, targetPos, yaw, pitch)
            refreshPassengerPositions(entity)
        }
    }

    private fun refreshPassengerPositions(entity: Entity) {
        VersionCompat.INSTANCE.entityGetPassengerList(entity).forEach { passenger ->
            val p = passenger as? Entity ?: return@forEach
            VersionCompat.INSTANCE.entityUpdatePassengerPosition(entity, p)
            refreshPassengerPositions(p)
        }
    }

    private fun rootEntity(entity: Entity): Entity {
        var current = entity
        while (VersionCompat.INSTANCE.entityGetVehicle(current) != null) {
            current = VersionCompat.INSTANCE.entityGetVehicle(current) as Entity
        }
        return current
    }

    private fun transformEntity(
        position: Vec3,
        direction: Vec3,
        sourceMin: BlockPos,
        sourceMax: BlockPos,
        operation: MoveEntitiesOperation,
    ): EntityTarget {
        val sizeX = sourceMax.x - sourceMin.x + 1.0
        val sizeZ = sourceMax.z - sourceMin.z + 1.0
        val relative = position.subtract(sourceMin.x.toDouble(), sourceMin.y.toDouble(), sourceMin.z.toDouble())
        val sizeY = sourceMax.y - sourceMin.y + 1.0
        val mirrored = when (operation.mirrorAxis) {
            EntityMoveMirrorAxis.NONE -> relative
            EntityMoveMirrorAxis.X -> Vec3(sizeX - relative.x, relative.y, relative.z)
            EntityMoveMirrorAxis.Y -> Vec3(relative.x, sizeY - relative.y, relative.z)
            EntityMoveMirrorAxis.Z -> Vec3(relative.x, relative.y, sizeZ - relative.z)
        }
        val rotatedPosition = rotatePosition(mirrored, sizeX, sizeZ, operation.rotationQuarterTurns)
        val transformedDirection = rotateDirection(mirrorDirection(direction, operation.mirrorAxis), operation.rotationQuarterTurns)
        val targetPosition = rotatedPosition.add(
            operation.destinationOrigin.x.toDouble(),
            operation.destinationOrigin.y.toDouble(),
            operation.destinationOrigin.z.toDouble(),
        )
        return EntityTarget(
            position = targetPosition,
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

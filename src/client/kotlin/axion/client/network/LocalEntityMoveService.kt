package axion.client.network

import axion.common.operation.EntityMoveMirrorAxis
import axion.common.operation.MoveEntitiesOperation
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

object LocalEntityMoveService {
    fun plan(world: World, operation: MoveEntitiesOperation): List<EntityMovePlan> {
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
            .map { entity ->
                val currentPos = Vec3d(entity.x, entity.y, entity.z)
                val target = transformEntity(currentPos, directionFromAngles(entity.yaw, entity.pitch), sourceMin, sourceMax, operation)
                EntityMovePlan(
                    entityId = entity.uuid,
                    fromPos = currentPos,
                    toPos = target.position,
                    fromYaw = entity.yaw,
                    fromPitch = entity.pitch,
                    toYaw = target.yaw,
                    toPitch = target.pitch,
                )
            }
            .toList()
    }

    fun apply(world: World, moves: List<EntityMovePlan>, reverse: Boolean = false) {
        val serverWorld = world as? ServerWorld ?: return
        moves.forEach { move ->
            val entity = serverWorld.getEntity(move.entityId) ?: return@forEach
            if (entity is PlayerEntity) {
                return@forEach
            }
            val targetPos = if (reverse) move.fromPos else move.toPos
            val yaw = if (reverse) move.fromYaw else move.toYaw
            val pitch = if (reverse) move.fromPitch else move.toPitch
            entity.refreshPositionAndAngles(targetPos.x, targetPos.y, targetPos.z, yaw, pitch)
        }
    }

    private fun rootEntity(entity: Entity): Entity {
        var current = entity
        while (current.vehicle != null) {
            current = current.vehicle!!
        }
        return current
    }

    private fun transformEntity(
        position: Vec3d,
        direction: Vec3d,
        sourceMin: BlockPos,
        sourceMax: BlockPos,
        operation: MoveEntitiesOperation,
    ): EntityTarget {
        val sizeX = sourceMax.x - sourceMin.x + 1.0
        val sizeZ = sourceMax.z - sourceMin.z + 1.0
        val relative = position.subtract(sourceMin.x.toDouble(), sourceMin.y.toDouble(), sourceMin.z.toDouble())
        val mirrored = when (operation.mirrorAxis) {
            EntityMoveMirrorAxis.NONE -> relative
            EntityMoveMirrorAxis.X -> Vec3d(sizeX - relative.x, relative.y, relative.z)
            EntityMoveMirrorAxis.Z -> Vec3d(relative.x, relative.y, sizeZ - relative.z)
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

package axion.server.fabric

import axion.protocol.IntVector3
import axion.protocol.MoveEntitiesRequest
import axion.protocol.PlacementMirrorAxisPayload
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

object AxionFabricEntityMoveService {
    fun apply(world: ServerWorld, operation: MoveEntitiesRequest) {
        move(world, operation)
    }

    fun move(world: ServerWorld, operation: MoveEntitiesRequest): List<FabricCommittedEntityMove> {
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
            .map { entity ->
                val target = transformEntity(
                    position = Vec3d(entity.x, entity.y, entity.z),
                    direction = directionFromAngles(entity.yaw, entity.pitch),
                    sourceMin = sourceMin,
                    sourceMax = sourceMax,
                    operation = operation,
                )
                val change = FabricCommittedEntityMove(
                    entityId = entity.uuid,
                    fromX = entity.x,
                    fromY = entity.y,
                    fromZ = entity.z,
                    fromYaw = entity.yaw,
                    fromPitch = entity.pitch,
                    toX = target.position.x,
                    toY = target.position.y,
                    toZ = target.position.z,
                    toYaw = target.yaw,
                    toPitch = target.pitch,
                )
                entity.refreshPositionAndAngles(target.position.x, target.position.y, target.position.z, target.yaw, target.pitch)
                refreshPassengerPositions(entity)
                change
            }
            .toList()
    }

    fun applyMoves(world: ServerWorld, moves: List<FabricCommittedEntityMove>, reverse: Boolean) {
        moves.forEach { move ->
            val entity = world.getEntity(move.entityId) ?: return@forEach
            if (entity is PlayerEntity || entity.isRemoved) {
                return@forEach
            }
            if (reverse) {
                entity.refreshPositionAndAngles(move.fromX, move.fromY, move.fromZ, move.fromYaw, move.fromPitch)
            } else {
                entity.refreshPositionAndAngles(move.toX, move.toY, move.toZ, move.toYaw, move.toPitch)
            }
            refreshPassengerPositions(entity)
        }
    }

    private fun refreshPassengerPositions(entity: Entity) {
        entity.passengerList.forEach { passenger ->
            entity.updatePassengerPosition(passenger)
            refreshPassengerPositions(passenger)
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
        sourceMin: IntVector3,
        sourceMax: IntVector3,
        operation: MoveEntitiesRequest,
    ): EntityTarget {
        val sizeX = sourceMax.x - sourceMin.x + 1.0
        val sizeY = sourceMax.y - sourceMin.y + 1.0
        val sizeZ = sourceMax.z - sourceMin.z + 1.0
        val relative = position.subtract(sourceMin.x.toDouble(), sourceMin.y.toDouble(), sourceMin.z.toDouble())
        val mirrored = when (operation.mirrorAxis) {
            PlacementMirrorAxisPayload.NONE -> relative
            PlacementMirrorAxisPayload.X -> Vec3d(sizeX - relative.x, relative.y, relative.z)
            PlacementMirrorAxisPayload.Y -> Vec3d(relative.x, sizeY - relative.y, relative.z)
            PlacementMirrorAxisPayload.Z -> Vec3d(relative.x, relative.y, sizeZ - relative.z)
        }
        val rotatedPosition = rotatePosition(mirrored, sizeX, sizeZ, operation.rotationQuarterTurns)
        val transformedDirection = rotateDirection(mirrorDirection(direction, operation.mirrorAxis), operation.rotationQuarterTurns)
        return EntityTarget(
            position = rotatedPosition.add(
                operation.destinationOrigin.x.toDouble(),
                operation.destinationOrigin.y.toDouble(),
                operation.destinationOrigin.z.toDouble(),
            ),
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
            PlacementMirrorAxisPayload.Y -> Vec3d(direction.x, -direction.y, direction.z)
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

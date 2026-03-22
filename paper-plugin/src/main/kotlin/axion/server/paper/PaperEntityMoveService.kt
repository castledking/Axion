package axion.server.paper

import axion.protocol.MoveEntitiesRequest
import axion.protocol.PlacementMirrorAxisPayload
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

object PaperEntityMoveService {
    fun move(world: World, operation: MoveEntitiesRequest): List<CommittedEntityMove> {
        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
        val sizeX = sourceMax.x - sourceMin.x + 1.0
        val sizeZ = sourceMax.z - sourceMin.z + 1.0
        val queryBox = BoundingBox(
            sourceMin.x.toDouble(),
            sourceMin.y.toDouble(),
            sourceMin.z.toDouble(),
            sourceMax.x + 1.0,
            sourceMax.y + 1.25,
            sourceMax.z + 1.0,
        )
        val seen = linkedSetOf<UUID>()
        return world.getNearbyEntities(queryBox)
            .asSequence()
            .map(::rootEntity)
            .filter { entity ->
                entity !is Player &&
                    entity.vehicle == null &&
                    entity.isValid &&
                    seen.add(entity.uniqueId)
            }
            .map { entity ->
                val target = transformLocation(entity.location, sourceMin.x, sourceMin.y, sourceMin.z, sizeX, sizeZ, operation)
                val from = entity.location.clone()
                entity.teleport(target)
                CommittedEntityMove(
                    entityId = entity.uniqueId,
                    from = from,
                    to = target.clone(),
                )
            }
            .toList()
    }

    fun applyMoves(world: World, moves: List<CommittedEntityMove>, reverse: Boolean) {
        moves.forEach { move ->
            val entity = world.entities.firstOrNull { it.uniqueId == move.entityId } ?: return@forEach
            if (entity is Player || !entity.isValid) {
                return@forEach
            }
            entity.teleport(if (reverse) move.from else move.to)
        }
    }

    private fun rootEntity(entity: Entity): Entity {
        var current = entity
        while (current.vehicle != null) {
            current = current.vehicle!!
        }
        return current
    }

    private fun transformLocation(
        location: Location,
        sourceMinX: Int,
        sourceMinY: Int,
        sourceMinZ: Int,
        sizeX: Double,
        sizeZ: Double,
        operation: MoveEntitiesRequest,
    ): Location {
        val relative = Vector(
            location.x - sourceMinX,
            location.y - sourceMinY,
            location.z - sourceMinZ,
        )
        val mirrored = when (operation.mirrorAxis) {
            PlacementMirrorAxisPayload.NONE -> relative
            PlacementMirrorAxisPayload.X -> Vector(sizeX - relative.x, relative.y, relative.z)
            PlacementMirrorAxisPayload.Z -> Vector(relative.x, relative.y, sizeZ - relative.z)
        }
        val rotatedPosition = rotatePosition(mirrored, sizeX, sizeZ, operation.rotationQuarterTurns)
        val transformedDirection = rotateDirection(mirrorDirection(location.direction, operation.mirrorAxis), operation.rotationQuarterTurns)
        val target = Location(
            location.world,
            operation.destinationOrigin.x + rotatedPosition.x,
            operation.destinationOrigin.y + rotatedPosition.y,
            operation.destinationOrigin.z + rotatedPosition.z,
            directionToYaw(transformedDirection),
            directionToPitch(transformedDirection),
        )
        target.direction = transformedDirection
        return target
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

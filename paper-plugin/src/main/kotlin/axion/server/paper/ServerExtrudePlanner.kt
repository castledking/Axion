package axion.server.paper

import axion.protocol.AxionExtrudeMode
import axion.protocol.ExtrudeRequest
import axion.protocol.IntVector3
import net.minecraft.core.BlockPos
import org.bukkit.Material
import org.bukkit.World
import java.util.ArrayDeque
import kotlin.math.abs

class ServerExtrudePlanner(
    private val world: World,
) {
    fun plan(request: ExtrudeRequest): PlannedExtrude? {
        val footprint = discoverPlanarFootprint(request)
        if (footprint.isEmpty()) {
            return null
        }

        val baseWrites = when (request.mode) {
            AxionExtrudeMode.EXTEND -> footprint.mapNotNull { source ->
                if (blockStateString(source) != request.expectedState) {
                    null
                } else {
                    val destination = IntVector3(
                        source.x + request.direction.x,
                        source.y + request.direction.y,
                        source.z + request.direction.z,
                    )
                    PlannedWrite(
                        pos = destination,
                        blockState = request.expectedState,
                        blockEntityData = PaperBlockEntitySnapshotService.capture(
                            world,
                            BlockPos(source.x, source.y, source.z),
                        ),
                    )
                }
            }

            AxionExtrudeMode.SHRINK -> footprint.mapNotNull { source ->
                if (blockStateString(source) != request.expectedState) {
                    null
                } else {
                    PlannedWrite(
                        pos = source,
                        blockState = Material.AIR.createBlockData().asString,
                        blockEntityData = null,
                    )
                }
            }
        }
        if (baseWrites.isEmpty()) {
            return null
        }

        val expandedWrites = mutableListOf<PlannedWrite>()
        ServerSymmetryTransformService.activeTransforms(request.symmetry).forEach { transform ->
            baseWrites.forEach { write ->
                expandedWrites += write.copy(
                    pos = request.symmetry?.let { config ->
                        ServerSymmetryTransformService.transformBlock(write.pos, config.anchor, transform)
                    } ?: write.pos,
                )
            }
        }

        return PlannedExtrude(
            footprintSize = footprint.size,
            writes = expandedWrites,
            touchedPositions = expandedWrites.mapTo(linkedSetOf()) { it.pos },
        )
    }

    private fun discoverPlanarFootprint(request: ExtrudeRequest): List<IntVector3> {
        val origin = request.origin
        if (blockStateString(origin) != request.expectedState) {
            return emptyList()
        }

        val queue = ArrayDeque<IntVector3>()
        val visited = linkedSetOf<IntVector3>()
        queue.add(origin)
        val axis = directionAxis(request.direction)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) {
                continue
            }

            planeNeighborOffsets(axis).forEach { offset ->
                val neighbor = IntVector3(
                    current.x + offset.x,
                    current.y + offset.y,
                    current.z + offset.z,
                )
                if (neighbor !in visited && isEligibleNeighbor(origin, neighbor, axis, request.expectedState)) {
                    queue.addLast(neighbor)
                }
            }
        }
        return visited.toList()
    }

    private fun isEligibleNeighbor(
        origin: IntVector3,
        candidate: IntVector3,
        axis: Axis,
        expectedState: String,
    ): Boolean {
        if (!isWithinPlanarRadius(origin, candidate, axis)) {
            return false
        }
        return blockStateString(candidate) == expectedState
    }

    private fun isWithinPlanarRadius(origin: IntVector3, candidate: IntVector3, axis: Axis): Boolean {
        return when (axis) {
            Axis.X -> candidate.x == origin.x &&
                abs(candidate.y - origin.y) <= MAX_RADIUS &&
                abs(candidate.z - origin.z) <= MAX_RADIUS

            Axis.Y -> candidate.y == origin.y &&
                abs(candidate.x - origin.x) <= MAX_RADIUS &&
                abs(candidate.z - origin.z) <= MAX_RADIUS

            Axis.Z -> candidate.z == origin.z &&
                abs(candidate.x - origin.x) <= MAX_RADIUS &&
                abs(candidate.y - origin.y) <= MAX_RADIUS
        }
    }

    private fun planeNeighborOffsets(axis: Axis): Array<IntVector3> {
        return when (axis) {
            Axis.X -> arrayOf(
                IntVector3(0, 1, 0),
                IntVector3(0, -1, 0),
                IntVector3(0, 0, 1),
                IntVector3(0, 0, -1),
            )

            Axis.Y -> arrayOf(
                IntVector3(1, 0, 0),
                IntVector3(-1, 0, 0),
                IntVector3(0, 0, 1),
                IntVector3(0, 0, -1),
            )

            Axis.Z -> arrayOf(
                IntVector3(1, 0, 0),
                IntVector3(-1, 0, 0),
                IntVector3(0, 1, 0),
                IntVector3(0, -1, 0),
            )
        }
    }

    private fun directionAxis(direction: IntVector3): Axis {
        return when {
            direction.x != 0 -> Axis.X
            direction.y != 0 -> Axis.Y
            else -> Axis.Z
        }
    }

    private fun blockStateString(pos: IntVector3): String {
        return world.getBlockAt(pos.x, pos.y, pos.z).blockData.getAsString(false)
    }

    data class PlannedExtrude(
        val footprintSize: Int,
        val writes: List<PlannedWrite>,
        val touchedPositions: Set<IntVector3>,
    )

    data class PlannedWrite(
        val pos: IntVector3,
        val blockState: String,
        val blockEntityData: String?,
    )

    private enum class Axis {
        X,
        Y,
        Z,
    }

    companion object {
        private const val MAX_RADIUS: Int = 32
    }
}

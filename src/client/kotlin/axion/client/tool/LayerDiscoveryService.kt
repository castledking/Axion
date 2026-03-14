package axion.client.tool

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i
import net.minecraft.world.BlockView
import java.util.ArrayDeque
import kotlin.math.abs

object LayerDiscoveryService {
    private const val MAX_RADIUS: Int = 32

    fun discoverPlanarFootprint(
        world: BlockView,
        origin: BlockPos,
        direction: Direction,
    ): List<BlockPos> {
        val sourceState = world.getBlockState(origin)
        if (sourceState.isAir) {
            return emptyList()
        }

        val queue = ArrayDeque<BlockPos>()
        val visited = linkedSetOf<BlockPos>()
        queue.add(origin.toImmutable())

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst().toImmutable()
            if (!visited.add(current)) {
                continue
            }

            planeNeighborOffsets(direction.axis).forEach { offset ->
                val neighbor = current.add(offset).toImmutable()
                if (neighbor !in visited && isEligibleNeighbor(world, origin, neighbor, direction.axis, sourceState)) {
                    queue.addLast(neighbor)
                }
            }
        }

        return visited.toList()
    }

    private fun isEligibleNeighbor(
        world: BlockView,
        origin: BlockPos,
        candidate: BlockPos,
        axis: Direction.Axis,
        sourceState: BlockState,
    ): Boolean {
        if (!isWithinPlanarRadius(origin, candidate, axis)) {
            return false
        }

        return world.getBlockState(candidate) == sourceState
    }

    private fun isWithinPlanarRadius(origin: BlockPos, candidate: BlockPos, axis: Direction.Axis): Boolean {
        return when (axis) {
            Direction.Axis.X -> candidate.x == origin.x &&
                abs(candidate.y - origin.y) <= MAX_RADIUS &&
                abs(candidate.z - origin.z) <= MAX_RADIUS

            Direction.Axis.Y -> candidate.y == origin.y &&
                abs(candidate.x - origin.x) <= MAX_RADIUS &&
                abs(candidate.z - origin.z) <= MAX_RADIUS

            Direction.Axis.Z -> candidate.z == origin.z &&
                abs(candidate.x - origin.x) <= MAX_RADIUS &&
                abs(candidate.y - origin.y) <= MAX_RADIUS
        }
    }

    private fun planeNeighborOffsets(axis: Direction.Axis): Array<Vec3i> {
        return when (axis) {
            Direction.Axis.X -> arrayOf(
                Vec3i(0, 1, 0),
                Vec3i(0, -1, 0),
                Vec3i(0, 0, 1),
                Vec3i(0, 0, -1),
            )

            Direction.Axis.Y -> arrayOf(
                Vec3i(1, 0, 0),
                Vec3i(-1, 0, 0),
                Vec3i(0, 0, 1),
                Vec3i(0, 0, -1),
            )

            Direction.Axis.Z -> arrayOf(
                Vec3i(1, 0, 0),
                Vec3i(-1, 0, 0),
                Vec3i(0, 1, 0),
                Vec3i(0, -1, 0),
            )
        }
    }
}

package axion.client.render

import axion.client.compat.unpackLongX
import axion.client.compat.unpackLongY
import axion.client.compat.unpackLongZ
import axion.common.model.ClipboardCell
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos

/**
 * Extracts the outside/cavity boundary of preview occupancy without consulting
 * block opacity. Translucent blocks must not recursively turn every cell behind
 * them into another preview surface.
 */
object PreviewSurfaceTopology {
    fun retainBoundaryCells(occupiedCells: List<ClipboardCell>): List<ClipboardCell> {
        if (occupiedCells.size <= 1) return occupiedCells
        val occupiedOffsets = LongArray(occupiedCells.size) { index ->
            val offset = occupiedCells[index].offset
            BlockPos.asLong(offset.x, offset.y, offset.z)
        }
        val boundaryOffsets = LongOpenHashSet(retainBoundaryOffsets(occupiedOffsets))
        return occupiedCells.filter { cell ->
            boundaryOffsets.contains(BlockPos.asLong(cell.offset.x, cell.offset.y, cell.offset.z))
        }
    }

    /** Pure packed-coordinate seam used by the geometry regression tests. */
    fun retainBoundaryOffsets(occupiedOffsets: LongArray): LongArray {
        if (occupiedOffsets.size <= 1) return occupiedOffsets.copyOf()
        val occupied = LongOpenHashSet(occupiedOffsets)
        val boundary = LongArray(occupiedOffsets.size)
        var count = 0
        occupiedOffsets.forEach { packed ->
            val x = unpackLongX(packed)
            val y = unpackLongY(packed)
            val z = unpackLongZ(packed)
            if (
                !occupied.contains(BlockPos.asLong(x - 1, y, z)) ||
                !occupied.contains(BlockPos.asLong(x + 1, y, z)) ||
                !occupied.contains(BlockPos.asLong(x, y - 1, z)) ||
                !occupied.contains(BlockPos.asLong(x, y + 1, z)) ||
                !occupied.contains(BlockPos.asLong(x, y, z - 1)) ||
                !occupied.contains(BlockPos.asLong(x, y, z + 1))
            ) {
                boundary[count++] = packed
            }
        }
        return boundary.copyOf(count)
    }
}

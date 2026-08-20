package axion.client.render.gpu

import axion.common.model.ClipboardCell
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos

/**
 * Keeps the exact block states needed while tessellating a precomputed preview
 * surface. A surface block can only query itself and its six direct neighbors
 * for side culling, fluids, and ambient occlusion, so deeper interior states do
 * not need to be expanded into the world-space session map.
 */
object PreviewStateHalo {
    fun retain(
        occupiedCells: List<ClipboardCell>,
        surfaceCells: List<ClipboardCell>,
    ): List<ClipboardCell> {
        if (surfaceCells.isEmpty() || occupiedCells.isEmpty()) return emptyList()
        if (surfaceCells.size == occupiedCells.size) return occupiedCells

        val surfaceOffsets = LongOpenHashSet(surfaceCells.size)
        surfaceCells.forEach { cell ->
            surfaceOffsets.add(BlockPos.asLong(cell.offset.x, cell.offset.y, cell.offset.z))
        }

        return occupiedCells.filter { cell ->
            val x = cell.offset.x
            val y = cell.offset.y
            val z = cell.offset.z
            surfaceOffsets.contains(BlockPos.asLong(x, y, z)) ||
                surfaceOffsets.contains(BlockPos.asLong(x - 1, y, z)) ||
                surfaceOffsets.contains(BlockPos.asLong(x + 1, y, z)) ||
                surfaceOffsets.contains(BlockPos.asLong(x, y - 1, z)) ||
                surfaceOffsets.contains(BlockPos.asLong(x, y + 1, z)) ||
                surfaceOffsets.contains(BlockPos.asLong(x, y, z - 1)) ||
                surfaceOffsets.contains(BlockPos.asLong(x, y, z + 1))
        }
    }
}

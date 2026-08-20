package axion.client.tool

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

data class ExtrudePreviewState(
    val origin: BlockPos,
    val footprint: List<BlockPos>,
    val sourceState: BlockState,
    val direction: Direction,
    val extrudePositions: List<BlockPos>,
)

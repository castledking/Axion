package axion.client.tool

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

data class ExtrudePreviewState(
    val origin: BlockPos,
    val footprint: List<BlockPos>,
    val sourceState: BlockState,
    val direction: Direction,
    val extrudePositions: List<BlockPos>,
)

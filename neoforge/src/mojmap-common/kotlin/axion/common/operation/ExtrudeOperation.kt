package axion.common.operation

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

data class ExtrudeOperation(
    val origin: BlockPos,
    val footprint: List<BlockPos>,
    val sourceState: BlockState,
    val direction: Direction,
    val mode: ExtrudeMode,
) : EditOperation {
    override val kind: String = "extrude"
}

enum class ExtrudeMode {
    EXTEND,
    SHRINK,
}

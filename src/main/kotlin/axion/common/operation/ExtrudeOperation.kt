package axion.common.operation

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

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

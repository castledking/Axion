package axion.common.operation

import axion.common.model.BlockEntityDataSnapshot
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos

data class SymmetryPlacementOperation(
    val placements: List<SymmetryBlockPlacement>,
) : EditOperation {
    override val kind: String = "symmetry_placement"
}

data class SymmetryBlockPlacement(
    val pos: BlockPos,
    val state: BlockState,
    val blockEntityData: BlockEntityDataSnapshot? = null,
)

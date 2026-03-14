package axion.common.history

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import axion.common.model.BlockEntityDataSnapshot

data class BlockChange(
    val pos: BlockPos,
    val oldState: BlockState,
    val newState: BlockState,
    val oldBlockEntityData: BlockEntityDataSnapshot? = null,
    val newBlockEntityData: BlockEntityDataSnapshot? = null,
)

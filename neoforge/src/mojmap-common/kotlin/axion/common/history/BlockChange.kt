package axion.common.history

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos
import axion.common.model.BlockEntityDataSnapshot

data class BlockChange(
    val pos: BlockPos,
    val oldState: BlockState,
    val newState: BlockState,
    val oldBlockEntityData: BlockEntityDataSnapshot? = null,
    val newBlockEntityData: BlockEntityDataSnapshot? = null,
)

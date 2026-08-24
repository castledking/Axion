package axion.client.network

import axion.common.model.BlockEntityDataSnapshot
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos

data class BlockWrite(
    val pos: BlockPos,
    val state: BlockState,
    val blockEntityData: BlockEntityDataSnapshot? = null,
)

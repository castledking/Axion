package axion.client.network

import axion.common.model.BlockEntityDataSnapshot
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos

data class BlockWrite(
    val pos: BlockPos,
    val state: BlockState,
    val blockEntityData: BlockEntityDataSnapshot? = null,
)

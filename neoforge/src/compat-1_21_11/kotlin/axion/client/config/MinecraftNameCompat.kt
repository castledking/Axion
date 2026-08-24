package axion.client.config

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

// Yarn-era alias: Block.defaultState -> mojmap defaultBlockState().
val Block.defaultState: BlockState
    get() = this.defaultBlockState()

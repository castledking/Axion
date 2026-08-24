package axion.client.compat

import axion.client.mode.ReplacePlacementPolicy
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos

fun normalizeReplacePlacementState(state: BlockState, hitResult: BlockHitResult, replacementPos: BlockPos): BlockState {
    if (!state.hasProperty(BlockStateProperties.SLAB_TYPE) || state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE) return state
    val half = ReplacePlacementPolicy.singleSlabHalf(hitResult.direction, hitResult.pos.y - replacementPos.y)
    return state.with(BlockStateProperties.SLAB_TYPE, if (half == ReplacePlacementPolicy.SlabHalf.TOP) SlabType.TOP else SlabType.BOTTOM)
}

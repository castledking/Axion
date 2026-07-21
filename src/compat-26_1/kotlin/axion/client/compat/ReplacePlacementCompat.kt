package axion.client.compat

import axion.client.mode.ReplacePlacementPolicy
import net.minecraft.block.BlockState
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.SlabType

fun normalizeReplacePlacementState(state: BlockState, hitResult: BlockHitResult, replacementPos: BlockPos): BlockState {
    if (!state.hasProperty(BlockStateProperties.SLAB_TYPE) || state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE) {
        return state
    }
    val half = ReplacePlacementPolicy.singleSlabHalf(
        hitResult.direction,
        hitResult.location.y - replacementPos.y,
    )
    return state.setValue(
        BlockStateProperties.SLAB_TYPE,
        if (half == ReplacePlacementPolicy.SlabHalf.TOP) SlabType.TOP else SlabType.BOTTOM,
    )
}

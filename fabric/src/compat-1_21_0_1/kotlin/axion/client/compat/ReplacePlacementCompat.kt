package axion.client.compat

import axion.client.mode.ReplacePlacementPolicy
import net.minecraft.block.BlockState
import net.minecraft.block.enums.SlabType
import net.minecraft.state.property.Properties
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos

fun normalizeReplacePlacementState(state: BlockState, hitResult: BlockHitResult, replacementPos: BlockPos): BlockState {
    if (!state.contains(Properties.SLAB_TYPE) || state.get(Properties.SLAB_TYPE) != SlabType.DOUBLE) {
        return state
    }
    val half = ReplacePlacementPolicy.singleSlabHalf(
        hitResult.side,
        hitResult.pos.y - replacementPos.y,
    )
    return state.with(
        Properties.SLAB_TYPE,
        if (half == ReplacePlacementPolicy.SlabHalf.TOP) SlabType.TOP else SlabType.BOTTOM,
    )
}

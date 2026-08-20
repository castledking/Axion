package axion.common.model

import net.minecraft.util.math.BlockPos

sealed interface SelectionState {
    data object Idle : SelectionState

    data class FirstCornerSet(val firstCorner: BlockPos) : SelectionState

    data class RegionDefined(
        val firstCorner: BlockPos,
        val secondCorner: BlockPos,
    ) : SelectionState {
        fun region(): BlockRegion = BlockRegion(firstCorner, secondCorner).normalized()
    }
}

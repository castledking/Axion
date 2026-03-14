package axion.client.tool

import axion.common.model.BlockRegion
import net.minecraft.util.math.BlockPos

sealed interface EraseToolState {
    data object Idle : EraseToolState

    data class FirstCornerSet(val firstCorner: BlockPos) : EraseToolState

    data class RegionDefined(
        val firstCorner: BlockPos,
        val region: BlockRegion,
    ) : EraseToolState
}

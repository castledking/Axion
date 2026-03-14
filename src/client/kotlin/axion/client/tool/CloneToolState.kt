package axion.client.tool

import axion.common.model.BlockRegion
import net.minecraft.util.math.BlockPos

sealed interface CloneToolState {
    data object Idle : CloneToolState

    data class FirstCornerSet(
        val mode: PlacementToolMode,
        val firstCorner: BlockPos,
    ) : CloneToolState

    data class RegionDefined(
        val mode: PlacementToolMode,
        val firstCorner: BlockPos,
        val region: BlockRegion,
    ) : CloneToolState

    data class PreviewingOffset(val preview: ClonePreviewState) : CloneToolState

    data class AwaitingConfirm(val preview: ClonePreviewState) : CloneToolState
}

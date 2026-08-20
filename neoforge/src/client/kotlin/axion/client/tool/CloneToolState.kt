package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
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
        val secondCorner: BlockPos,
        val region: BlockRegion,
        val clipboardBuffer: ClipboardBuffer? = null,
    ) : CloneToolState

    data class PreviewingOffset(val preview: ClonePreviewState) : CloneToolState

    data class AwaitingConfirm(val preview: ClonePreviewState) : CloneToolState
}

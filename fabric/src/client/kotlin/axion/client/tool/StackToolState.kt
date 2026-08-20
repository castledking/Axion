package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.BlockPos

sealed interface StackToolState {
    data object Idle : StackToolState

    data class FirstCornerSet(val firstCorner: BlockPos) : StackToolState

    data class RegionDefined(
        val firstCorner: BlockPos,
        val secondCorner: BlockPos,
        val region: BlockRegion,
        val clipboardBuffer: ClipboardBuffer? = null,
    ) : StackToolState

    data class PreviewingStack(val preview: StackPreviewState) : StackToolState
}

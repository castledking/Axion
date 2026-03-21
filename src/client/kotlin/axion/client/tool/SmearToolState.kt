package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.BlockPos

sealed interface SmearToolState {
    data object Idle : SmearToolState

    data class FirstCornerSet(val firstCorner: BlockPos) : SmearToolState

    data class RegionDefined(
        val firstCorner: BlockPos,
        val secondCorner: BlockPos,
        val region: BlockRegion,
        val clipboardBuffer: ClipboardBuffer? = null,
    ) : SmearToolState

    data class PreviewingSmear(val preview: SmearPreviewState) : SmearToolState
}

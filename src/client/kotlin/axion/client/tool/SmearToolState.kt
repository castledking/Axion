package axion.client.tool

import axion.common.model.BlockRegion
import net.minecraft.util.math.BlockPos

sealed interface SmearToolState {
    data object Idle : SmearToolState

    data class FirstCornerSet(val firstCorner: BlockPos) : SmearToolState

    data class RegionDefined(
        val firstCorner: BlockPos,
        val region: BlockRegion,
    ) : SmearToolState

    data class PreviewingSmear(val preview: SmearPreviewState) : SmearToolState
}

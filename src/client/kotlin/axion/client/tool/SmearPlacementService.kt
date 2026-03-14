package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient

object SmearPlacementService {
    fun createInitialPreview(
        client: MinecraftClient,
        firstCorner: net.minecraft.util.math.BlockPos,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        scrollAmount: Double,
    ): SmearPreviewState? {
        return RegionRepeatPlacementService.createInitialPreview(
            client = client,
            firstCorner = firstCorner,
            sourceRegion = sourceRegion,
            clipboardBuffer = clipboardBuffer,
            scrollAmount = scrollAmount,
        )
    }

    fun nudgePreview(preview: SmearPreviewState, scrollAmount: Double): SmearPreviewState? {
        return RegionRepeatPlacementService.nudgePreview(preview, scrollAmount)
    }
}

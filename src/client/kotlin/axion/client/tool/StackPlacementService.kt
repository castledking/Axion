package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient

object StackPlacementService {
    fun createInitialPreview(
        client: MinecraftClient,
        firstCorner: net.minecraft.util.math.BlockPos,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        scrollAmount: Double,
    ): StackPreviewState? {
        return RegionRepeatPlacementService.createInitialPreview(
            client = client,
            firstCorner = firstCorner,
            sourceRegion = sourceRegion,
            clipboardBuffer = clipboardBuffer,
            scrollAmount = scrollAmount,
            mode = RegionRepeatPlacementService.Mode.STACK,
        )
    }

    fun nudgePreview(client: MinecraftClient, preview: StackPreviewState, scrollAmount: Double): StackPreviewState? {
        return RegionRepeatPlacementService.nudgePreview(
            client = client,
            preview = preview,
            scrollAmount = scrollAmount,
            mode = RegionRepeatPlacementService.Mode.STACK,
        )
    }
}

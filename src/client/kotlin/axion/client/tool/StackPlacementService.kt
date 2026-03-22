package axion.client.tool

import axion.client.AxionClientState
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
            mode = repeatMode(),
        )
    }

    fun nudgePreview(client: MinecraftClient, preview: StackPreviewState, scrollAmount: Double): StackPreviewState? {
        return RegionRepeatPlacementService.nudgePreview(
            client = client,
            preview = preview,
            scrollAmount = scrollAmount,
            mode = repeatMode(),
        )
    }

    fun repeatMode(): RegionRepeatPlacementService.Mode {
        return if (AxionClientState.keepExistingEnabled) {
            RegionRepeatPlacementService.Mode.SMEAR
        } else {
            RegionRepeatPlacementService.Mode.STACK
        }
    }
}

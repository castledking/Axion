package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

data class ClonePreviewState(
    val mode: PlacementToolMode,
    val firstCorner: BlockPos,
    val sourceRegion: BlockRegion,
    val sourceClipboardBuffer: ClipboardBuffer,
    val destinationClipboardBuffer: ClipboardBuffer,
    val anchor: BlockPos,
    val offset: Vec3i,
    val destinationRegion: BlockRegion,
    val transform: PlacementTransform = PlacementTransform(),
)

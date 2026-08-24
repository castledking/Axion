package axion.client.itemStack

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import axion.protocol.EntitySelectionMask

data class RepeatPreviewSegment(
    val sourceRegion: BlockRegion,
    val clipboardBuffer: ClipboardBuffer,
    val entitySelection: EntitySelectionMask,
    val step: Vec3i,
    val repeatCount: Int,
    val lookDirection: Direction,
    val scrollSign: Int,
)

data class RepeatRegionPreview(
    val firstCorner: BlockPos,
    val sourceRegion: BlockRegion,
    val clipboardBuffer: ClipboardBuffer,
    val entitySelection: EntitySelectionMask,
    val lookDirection: Direction,
    val step: Vec3i,
    val scrollSign: Int,
    val repeatCount: Int,
    val committedSegments: List<RepeatPreviewSegment> = emptyList(),
    val transform: PlacementTransform = PlacementTransform(),
    val sourceClipboardBuffer: ClipboardBuffer = clipboardBuffer,
)

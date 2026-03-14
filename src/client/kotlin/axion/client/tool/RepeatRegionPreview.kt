package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

data class RepeatRegionPreview(
    val firstCorner: BlockPos,
    val sourceRegion: BlockRegion,
    val clipboardBuffer: ClipboardBuffer,
    val step: Vec3i,
    val scrollSign: Int,
    val repeatCount: Int,
    val destinationRegions: List<BlockRegion>,
)

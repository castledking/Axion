package axion.common.operation

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.Vec3i

data class StackRegionOperation(
    val sourceRegion: BlockRegion,
    val clipboardBuffer: ClipboardBuffer,
    val step: Vec3i,
    val repeatCount: Int,
) : EditOperation {
    override val kind: String = "stack_region"
}

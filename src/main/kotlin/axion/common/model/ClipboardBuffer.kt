package axion.common.model

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

data class ClipboardBuffer(
    val size: Vec3i,
    val cells: List<ClipboardCell>,
) {
    fun nonAirCells(): List<ClipboardCell> = cells.filterNot { it.state.isAir }
}

data class ClipboardCell(
    val offset: Vec3i,
    val state: BlockState,
    val blockEntityData: BlockEntityDataSnapshot? = null,
) {
    fun absolutePos(origin: BlockPos): BlockPos = origin.add(offset)
}

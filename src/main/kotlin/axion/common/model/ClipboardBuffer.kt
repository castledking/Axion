package axion.common.model

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

data class ClipboardBuffer(
    val size: Vec3i,
    val cells: List<ClipboardCell>,
) {
    private val cachedNonAirCells: List<ClipboardCell> by lazy(LazyThreadSafetyMode.NONE) {
        cells.filterNot { it.state.isAir }
    }
    private val cachedHashCode: Int by lazy(LazyThreadSafetyMode.NONE) {
        31 * size.hashCode() + cells.hashCode()
    }

    fun nonAirCells(): List<ClipboardCell> = cachedNonAirCells

    override fun hashCode(): Int = cachedHashCode
}

data class ClipboardCell(
    val offset: Vec3i,
    val state: BlockState,
    val blockEntityData: BlockEntityDataSnapshot? = null,
) {
    fun absolutePos(origin: BlockPos): BlockPos = origin.add(offset)
}

package axion.client.network

import axion.common.operation.EditOperation
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneRegionOperation
import axion.common.operation.CloneEntitiesOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.ExtrudeOperation
import axion.common.operation.MoveEntitiesOperation
import axion.common.operation.OperationValidator
import axion.common.operation.SmearRegionOperation
import axion.common.operation.StackRegionOperation
import axion.common.operation.SymmetryPlacementOperation

class PermissiveOperationValidator : OperationValidator {
    var lastFailureMessage: String? = null
        private set

    override fun validate(operation: EditOperation): Boolean {
        lastFailureMessage = validationMessage(operation)
        return lastFailureMessage == null
    }

    private fun validationMessage(operation: EditOperation): String? {
        val estimate = estimate(operation)
        if (estimate.clipboardCells > MAX_CLIPBOARD_CELLS) {
            return "Axion edit canceled: clipboard preview exceeds $MAX_CLIPBOARD_CELLS cells."
        }
        if (estimate.extrudeFootprint > MAX_EXTRUDE_FOOTPRINT) {
            return "Axion edit canceled: extrude footprint exceeds $MAX_EXTRUDE_FOOTPRINT blocks."
        }
        if (estimate.extrudeWrites > MAX_EXTRUDE_WRITES) {
            return "Axion edit canceled: extrude write count exceeds $MAX_EXTRUDE_WRITES blocks."
        }
        if (estimate.blocksPerBatch > MAX_BLOCKS_PER_BATCH) {
            return "Axion edit canceled: estimated touched block count exceeds $MAX_BLOCKS_PER_BATCH blocks."
        }
        if (estimate.totalWrites > MAX_TOTAL_WRITES) {
            return "Axion edit canceled: estimated write count exceeds $MAX_TOTAL_WRITES blocks."
        }
        return null
    }

    private fun estimate(operation: EditOperation): OperationEstimate {
        return when (operation) {
            is ClearRegionOperation -> OperationEstimate(
                totalWrites = operation.region.volume(),
                blocksPerBatch = operation.region.volume(),
            )
            is CloneRegionOperation -> OperationEstimate(
                totalWrites = operation.sourceRegion.volume(),
                blocksPerBatch = operation.sourceRegion.volume(),
            )
            is CloneEntitiesOperation -> OperationEstimate()
            is StackRegionOperation -> OperationEstimate(
                totalWrites = operation.clipboardBuffer.cells.size.toLong() * operation.repeatCount,
                clipboardCells = operation.clipboardBuffer.cells.size,
                blocksPerBatch = operation.clipboardBuffer.cells.size.toLong() * operation.repeatCount,
            )
            is SmearRegionOperation -> OperationEstimate(
                totalWrites = operation.clipboardBuffer.cells.size.toLong() * operation.repeatCount,
                clipboardCells = operation.clipboardBuffer.cells.size,
                blocksPerBatch = operation.clipboardBuffer.cells.size.toLong() * operation.repeatCount,
            )
            is ExtrudeOperation -> OperationEstimate(
                totalWrites = operation.footprint.size.toLong(),
                blocksPerBatch = operation.footprint.size.toLong(),
                extrudeFootprint = operation.footprint.size,
                extrudeWrites = operation.footprint.size,
            )
            is MoveEntitiesOperation -> OperationEstimate()
            is SymmetryPlacementOperation -> OperationEstimate(
                totalWrites = operation.placements.size.toLong(),
                blocksPerBatch = operation.placements.size.toLong(),
            )
            is CompositeOperation -> operation.operations
                .map(::estimate)
                .fold(OperationEstimate(), OperationEstimate::plus)
            else -> OperationEstimate()
        }
    }

    private data class OperationEstimate(
        val totalWrites: Long = 0,
        val blocksPerBatch: Long = 0,
        val clipboardCells: Int = 0,
        val extrudeFootprint: Int = 0,
        val extrudeWrites: Int = 0,
    ) {
        operator fun plus(other: OperationEstimate): OperationEstimate {
            return OperationEstimate(
                totalWrites = totalWrites + other.totalWrites,
                blocksPerBatch = blocksPerBatch + other.blocksPerBatch,
                clipboardCells = maxOf(clipboardCells, other.clipboardCells),
                extrudeFootprint = maxOf(extrudeFootprint, other.extrudeFootprint),
                extrudeWrites = extrudeWrites + other.extrudeWrites,
            )
        }
    }

    private fun axion.common.model.BlockRegion.volume(): Long {
        val size = size()
        return size.x.toLong() * size.y.toLong() * size.z.toLong()
    }

    private companion object {
        const val MAX_BLOCKS_PER_BATCH: Long = 262_144
        const val MAX_TOTAL_WRITES: Long = 2_097_152
        const val MAX_CLIPBOARD_CELLS: Int = 262_144
        const val MAX_EXTRUDE_FOOTPRINT: Int = 32_768
        const val MAX_EXTRUDE_WRITES: Int = 32_768
    }
}

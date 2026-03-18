package axion.client.network

import axion.common.model.BlockEntityDataSnapshot
import axion.common.model.BlockRegion
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneRegionOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.operation.ExtrudeMode
import axion.common.operation.ExtrudeOperation
import axion.common.operation.SmearRegionOperation
import axion.common.operation.StackRegionOperation
import axion.common.operation.SymmetryPlacementOperation
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import net.minecraft.world.World

class LocalWritePlanner {
    fun plan(world: World, operation: EditOperation): WritePlan {
        val overlay = linkedMapOf<BlockPos, BlockWrite>()
        val writes = mutableListOf<BlockWrite>()
        appendWrites(world, operation, overlay, writes)
        return WritePlan(
            label = operationLabel(operation),
            writes = writes,
        )
    }

    private fun appendWrites(
        world: World,
        operation: EditOperation,
        overlay: MutableMap<BlockPos, BlockWrite>,
        writes: MutableList<BlockWrite>,
    ) {
        when (operation) {
            is CloneRegionOperation -> appendClone(world, operation, overlay, writes)
            is ClearRegionOperation -> appendClear(operation, overlay, writes)
            is StackRegionOperation -> appendStack(operation, overlay, writes)
            is SmearRegionOperation -> appendSmear(world, operation, overlay, writes)
            is ExtrudeOperation -> appendExtrude(world, operation, overlay, writes)
            is SymmetryPlacementOperation -> appendSymmetryPlacement(operation, overlay, writes)
            is CompositeOperation -> operation.operations.forEach { nested ->
                appendWrites(world, nested, overlay, writes)
            }
        }
    }

    private fun appendClone(
        world: World,
        operation: CloneRegionOperation,
        overlay: MutableMap<BlockPos, BlockWrite>,
        writes: MutableList<BlockWrite>,
    ) {
        captureRegionCells(world, operation.sourceRegion, overlay).forEach { cell ->
            appendWrite(
                pos = operation.destinationOrigin.add(cell.offset),
                state = cell.state,
                blockEntityData = cell.blockEntityData,
                overlay = overlay,
                writes = writes,
            )
        }
    }

    private fun appendClear(
        operation: ClearRegionOperation,
        overlay: MutableMap<BlockPos, BlockWrite>,
        writes: MutableList<BlockWrite>,
    ) {
        val region = operation.region.normalized()
        BlockPos.iterate(region.minCorner(), region.maxCorner()).forEach { pos ->
            appendWrite(pos.toImmutable(), Blocks.AIR.defaultState, null, overlay, writes)
        }
    }

    private fun appendStack(
        operation: StackRegionOperation,
        overlay: MutableMap<BlockPos, BlockWrite>,
        writes: MutableList<BlockWrite>,
    ) {
        if (operation.repeatCount <= 0) {
            return
        }

        val source = operation.sourceRegion.normalized()
        for (index in 1..operation.repeatCount) {
            val destinationOrigin = source.minCorner().add(operation.step.multiply(index))
            operation.clipboardBuffer.cells.forEach { cell ->
                appendWrite(destinationOrigin.add(cell.offset), cell.state, cell.blockEntityData, overlay, writes)
            }
        }
    }

    private fun appendSmear(
        world: World,
        operation: SmearRegionOperation,
        overlay: MutableMap<BlockPos, BlockWrite>,
        writes: MutableList<BlockWrite>,
    ) {
        if (operation.repeatCount <= 0) {
            return
        }

        val source = operation.sourceRegion.normalized()
        for (index in 1..operation.repeatCount) {
            val destinationOrigin = source.minCorner().add(operation.step.multiply(index))
            operation.clipboardBuffer.cells.forEach { cell ->
                val destinationPos = destinationOrigin.add(cell.offset).toImmutable()
                if (!currentStateAt(world, overlay, destinationPos).isAir) {
                    return@forEach
                }

                appendWrite(destinationPos, cell.state, cell.blockEntityData, overlay, writes)
            }
        }
    }

    private fun appendExtrude(
        world: World,
        operation: ExtrudeOperation,
        overlay: MutableMap<BlockPos, BlockWrite>,
        writes: MutableList<BlockWrite>,
    ) {
        when (operation.mode) {
            ExtrudeMode.EXTEND -> {
                operation.footprint.forEach { sourcePos ->
                    if (currentStateAt(world, overlay, sourcePos) != operation.sourceState) {
                        return@forEach
                    }

                    appendWrite(
                        pos = sourcePos.add(operation.direction.vector),
                        state = operation.sourceState,
                        blockEntityData = currentBlockEntityAt(world, overlay, sourcePos),
                        overlay = overlay,
                        writes = writes,
                    )
                }
            }

            ExtrudeMode.SHRINK -> {
                operation.footprint.forEach { sourcePos ->
                    if (currentStateAt(world, overlay, sourcePos) == operation.sourceState) {
                        appendWrite(sourcePos, Blocks.AIR.defaultState, null, overlay, writes)
                    }
                }
            }
        }
    }

    private fun appendSymmetryPlacement(
        operation: SymmetryPlacementOperation,
        overlay: MutableMap<BlockPos, BlockWrite>,
        writes: MutableList<BlockWrite>,
    ) {
        operation.placements.forEach { placement ->
            appendWrite(placement.pos, placement.state, placement.blockEntityData, overlay, writes)
        }
    }

    private fun appendWrite(
        pos: BlockPos,
        state: BlockState,
        blockEntityData: BlockEntityDataSnapshot?,
        overlay: MutableMap<BlockPos, BlockWrite>,
        writes: MutableList<BlockWrite>,
    ) {
        val immutablePos = pos.toImmutable()
        val write = BlockWrite(immutablePos, state, blockEntityData?.copy())
        overlay[immutablePos] = write
        writes += write
    }

    private fun captureRegionCells(
        world: World,
        region: BlockRegion,
        overlay: MutableMap<BlockPos, BlockWrite>,
    ): List<CapturedCell> {
        val normalized = region.normalized()
        val min = normalized.minCorner()
        val max = normalized.maxCorner()
        return buildList {
            for (pos in BlockPos.iterate(min, max)) {
                add(
                    CapturedCell(
                        offset = Vec3i(pos.x - min.x, pos.y - min.y, pos.z - min.z),
                        state = currentStateAt(world, overlay, pos),
                        blockEntityData = currentBlockEntityAt(world, overlay, pos),
                    ),
                )
            }
        }
    }

    private fun currentStateAt(
        world: World,
        overlay: Map<BlockPos, BlockWrite>,
        pos: BlockPos,
    ): BlockState {
        return overlay[pos]?.state ?: world.getBlockState(pos)
    }

    private fun currentBlockEntityAt(
        world: World,
        overlay: Map<BlockPos, BlockWrite>,
        pos: BlockPos,
    ): BlockEntityDataSnapshot? {
        return overlay[pos]?.blockEntityData?.copy() ?: BlockEntitySnapshotService.capture(world, pos)
    }

    private fun operationLabel(operation: EditOperation): String {
        return when (operation) {
            is CloneRegionOperation -> "Clone"
            is ClearRegionOperation -> "Erase"
            is StackRegionOperation -> "Stack"
            is SmearRegionOperation -> "Smear"
            is ExtrudeOperation -> "Extrude"
            is SymmetryPlacementOperation -> "Place"
            is CompositeOperation -> compositeLabel(operation)
            else -> "Edit"
        }
    }

    private fun compositeLabel(operation: CompositeOperation): String {
        val hasClone = operation.operations.any { it is CloneRegionOperation }
        val hasClear = operation.operations.any { it is ClearRegionOperation }
        return when {
            hasClone && hasClear -> "Move"
            hasClone -> "Clone"
            else -> operation.operations.firstOrNull()?.let(::operationLabel) ?: "Edit"
        }
    }

    private data class CapturedCell(
        val offset: Vec3i,
        val state: BlockState,
        val blockEntityData: BlockEntityDataSnapshot?,
    )
}

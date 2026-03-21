package axion.client.tool

import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneRegionOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.operation.SymmetryBlockPlacement
import axion.common.operation.SymmetryPlacementOperation
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos

object PlacementCommitService {
    fun toOperation(preview: ClonePreviewState): EditOperation {
        val cloneOperation = if (preview.transform.isIdentity() && !regionsOverlap(preview.sourceRegion, preview.destinationRegion)) {
            CloneRegionOperation(
                sourceRegion = preview.sourceRegion,
                destinationOrigin = preview.destinationRegion.minCorner(),
            )
        } else {
            buildClonePlacementOperation(preview)
        }

        return when (preview.mode) {
            PlacementToolMode.CLONE -> cloneOperation
            PlacementToolMode.MOVE -> buildMoveOperation(preview, cloneOperation)
        }
    }

    private fun buildClonePlacementOperation(preview: ClonePreviewState): SymmetryPlacementOperation {
        val sourceRegion = preview.sourceRegion.normalized()
        return SymmetryPlacementOperation(
            preview.destinationClipboardBuffer.cells.mapNotNull { cell ->
                val destinationPos = preview.destinationRegion.minCorner().add(cell.offset).toImmutable()
                if (sourceRegion.contains(destinationPos) && cell.state.isAir) {
                    null
                } else {
                    SymmetryBlockPlacement(
                        pos = destinationPos,
                        state = cell.state,
                        blockEntityData = cell.blockEntityData?.copy(),
                    )
                }
            },
        )
    }

    private fun buildMoveOperation(
        preview: ClonePreviewState,
        cloneOperation: EditOperation,
    ): EditOperation {
        if (preview.transform.isIdentity() && !regionsOverlap(preview.sourceRegion, preview.destinationRegion)) {
            return CompositeOperation(
                listOf(
                    cloneOperation,
                    ClearRegionOperation(preview.sourceRegion),
                ),
            )
        }

        val destinationPlacements = preview.destinationClipboardBuffer.cells.map { cell ->
            SymmetryBlockPlacement(
                pos = preview.destinationRegion.minCorner().add(cell.offset),
                state = cell.state,
                blockEntityData = cell.blockEntityData?.copy(),
            )
        }
        val destinationPositions = destinationPlacements.mapTo(linkedSetOf()) { it.pos.toImmutable() }
        val sourceOnlyAirPlacements = buildList {
            val source = preview.sourceRegion.normalized()
            for (pos in BlockPos.iterate(source.minCorner(), source.maxCorner())) {
                val immutablePos = pos.toImmutable()
                if (immutablePos !in destinationPositions) {
                    add(
                        SymmetryBlockPlacement(
                            pos = immutablePos,
                            state = Blocks.AIR.defaultState,
                            blockEntityData = null,
                        ),
                    )
                }
            }
        }

        return SymmetryPlacementOperation(
            placements = sourceOnlyAirPlacements + destinationPlacements,
        )
    }

    private fun regionsOverlap(a: axion.common.model.BlockRegion, b: axion.common.model.BlockRegion): Boolean {
        val left = a.normalized()
        val right = b.normalized()
        return left.minCorner().x <= right.maxCorner().x &&
            left.maxCorner().x >= right.minCorner().x &&
            left.minCorner().y <= right.maxCorner().y &&
            left.maxCorner().y >= right.minCorner().y &&
            left.minCorner().z <= right.maxCorner().z &&
            left.maxCorner().z >= right.minCorner().z
    }
}

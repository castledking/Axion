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
        val cloneOperation = if (preview.transform.isIdentity() &&
            !regionsOverlap(preview.sourceRegion, preview.destinationRegion) &&
            isFullCuboidCapture(preview.sourceRegion, preview.sourceClipboardBuffer)
        ) {
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
        if (preview.transform.isIdentity() &&
            !regionsOverlap(preview.sourceRegion, preview.destinationRegion) &&
            isFullCuboidCapture(preview.sourceRegion, preview.sourceClipboardBuffer)
        ) {
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
        val sourcePositions = preview.sourceClipboardBuffer.cells.mapTo(linkedSetOf()) { cell ->
            preview.sourceRegion.minCorner().add(cell.offset).toImmutable()
        }
        val sourceOnlyAirPlacements = buildList {
            for (immutablePos in sourcePositions) {
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

    private fun isFullCuboidCapture(
        region: axion.common.model.BlockRegion,
        clipboardBuffer: axion.common.model.ClipboardBuffer,
    ): Boolean {
        val size = region.normalized().size()
        return (size.x * size.y * size.z) == clipboardBuffer.cells.size
    }
}

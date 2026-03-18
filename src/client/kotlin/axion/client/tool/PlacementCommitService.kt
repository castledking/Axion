package axion.client.tool

import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneRegionOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.operation.SymmetryBlockPlacement
import axion.common.operation.SymmetryPlacementOperation

object PlacementCommitService {
    fun toOperation(preview: ClonePreviewState): EditOperation {
        val cloneOperation = if (preview.transform.isIdentity()) {
            CloneRegionOperation(
                sourceRegion = preview.sourceRegion,
                destinationOrigin = preview.destinationRegion.minCorner(),
            )
        } else {
            SymmetryPlacementOperation(
                preview.destinationClipboardBuffer.cells.map { cell ->
                    SymmetryBlockPlacement(
                        pos = preview.destinationRegion.minCorner().add(cell.offset),
                        state = cell.state,
                        blockEntityData = cell.blockEntityData?.copy(),
                    )
                },
            )
        }

        return when (preview.mode) {
            PlacementToolMode.CLONE -> cloneOperation
            PlacementToolMode.MOVE -> CompositeOperation(
                listOf(
                    cloneOperation,
                    ClearRegionOperation(preview.sourceRegion),
                ),
            )
        }
    }
}

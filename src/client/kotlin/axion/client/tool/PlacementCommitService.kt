package axion.client.tool

import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneRegionOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation

object PlacementCommitService {
    fun toOperation(preview: ClonePreviewState): EditOperation {
        val cloneOperation = CloneRegionOperation(
            sourceRegion = preview.sourceRegion,
            destinationOrigin = preview.destinationRegion.minCorner(),
        )

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

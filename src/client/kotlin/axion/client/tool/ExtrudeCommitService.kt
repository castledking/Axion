package axion.client.tool

import axion.common.operation.ExtrudeMode
import axion.common.operation.ExtrudeOperation

object ExtrudeCommitService {
    fun toOperation(preview: ExtrudePreviewState, mode: ExtrudeMode): ExtrudeOperation {
        return ExtrudeOperation(
            origin = preview.origin,
            footprint = preview.footprint,
            sourceState = preview.sourceState,
            direction = preview.direction,
            mode = mode,
        )
    }
}

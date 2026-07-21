package axion.server.paper

import axion.protocol.AxionRemoteOperation
import axion.protocol.ClearRegionRequest
import axion.protocol.CloneEntitiesRequest
import axion.protocol.CloneRegionRequest
import axion.protocol.DeleteEntitiesRequest
import axion.protocol.ExtrudeRequest
import axion.protocol.FilteredCloneRegionRequest
import axion.protocol.MoveEntitiesRequest
import axion.protocol.PlaceBlocksRequest
import axion.protocol.SmearRegionRequest
import axion.protocol.StackRegionRequest

internal object AxionOperationToolPolicy {
    fun primaryTool(operations: List<AxionRemoteOperation>): AxionToolKind? {
        val hasClone = operations.any { it is CloneRegionRequest }
        val hasFilteredClone = operations.any { it is FilteredCloneRegionRequest }
        val hasClear = operations.any { it is ClearRegionRequest }
        val hasStack = operations.any { it is StackRegionRequest }
        val hasSmear = operations.any { it is SmearRegionRequest }
        val hasExtrude = operations.any { it is ExtrudeRequest }
        val hasPlace = operations.any { it is PlaceBlocksRequest }
        val hasDeleteEntities = operations.any { it is DeleteEntitiesRequest }
        val hasMoveEntities = operations.any { it is MoveEntitiesRequest }
        return when {
            hasMoveEntities -> AxionToolKind.MOVE
            hasClone && hasClear && operations.all {
                it is CloneRegionRequest || it is ClearRegionRequest
            } -> AxionToolKind.MOVE
            hasStack -> AxionToolKind.STACK
            hasSmear -> AxionToolKind.SMEAR
            hasExtrude -> AxionToolKind.EXTRUDE
            hasClone || hasFilteredClone || operations.any { it is CloneEntitiesRequest } -> AxionToolKind.CLONE
            hasDeleteEntities || hasClear -> AxionToolKind.ERASE
            hasPlace -> null
            else -> null
        }
    }

    fun requiredTools(operations: List<AxionRemoteOperation>): Set<AxionToolKind> {
        primaryTool(operations)?.let { return setOf(it) }
        return operations.mapNotNullTo(linkedSetOf()) { operation ->
            when (operation) {
                is ClearRegionRequest -> AxionToolKind.ERASE
                is CloneRegionRequest -> AxionToolKind.CLONE
                is FilteredCloneRegionRequest -> AxionToolKind.CLONE
                is CloneEntitiesRequest -> AxionToolKind.CLONE
                is DeleteEntitiesRequest -> AxionToolKind.ERASE
                is MoveEntitiesRequest -> AxionToolKind.MOVE
                is StackRegionRequest -> AxionToolKind.STACK
                is SmearRegionRequest -> AxionToolKind.SMEAR
                is ExtrudeRequest -> AxionToolKind.EXTRUDE
                is PlaceBlocksRequest -> null
            }
        }
    }
}

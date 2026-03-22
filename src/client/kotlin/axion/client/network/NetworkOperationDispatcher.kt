package axion.client.network

import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneRegionOperation
import axion.common.operation.CloneEntitiesOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.operation.ExtrudeOperation
import axion.common.operation.MoveEntitiesOperation
import axion.common.operation.OperationDispatcher
import axion.common.operation.SmearRegionOperation
import axion.common.operation.StackRegionOperation
import axion.common.operation.SymmetryPlacementOperation
import axion.protocol.AxionExtrudeMode
import axion.protocol.AxionRemoteOperation
import axion.protocol.ClipboardCellPayload
import axion.protocol.ClearRegionRequest
import axion.protocol.CloneEntitiesRequest
import axion.protocol.CloneRegionRequest
import axion.protocol.ExtrudeRequest
import axion.protocol.IntVector3
import axion.protocol.MoveEntitiesRequest
import axion.protocol.OperationBatchRequest
import axion.protocol.PlacementMirrorAxisPayload
import axion.protocol.PlaceBlocksRequest
import axion.protocol.PlacedBlockPayload
import axion.protocol.SmearRegionRequest
import axion.protocol.StackRegionRequest
import net.minecraft.command.argument.BlockArgumentParser

class NetworkOperationDispatcher(
    private val recordHistory: Boolean = true,
) : OperationDispatcher {
    override fun dispatch(operation: EditOperation) {
        val flattened = flattenOperations(operation)
        val remoteOperations = flattened.mapNotNull(::toRemoteOperation)
        if (remoteOperations.size != flattened.size) {
            AxionServerConnection.notifyPlayerOnce("This Axion tool is not server-backed yet.")
            return
        }

        val requiredCapabilities = remoteOperations.mapTo(linkedSetOf()) { it.type }
        if (!AxionServerConnection.supportsAll(requiredCapabilities)) {
            AxionServerConnection.notifyPlayerOnce(
                when (AxionServerConnection.state()) {
                    is AxionServerConnection.State.AwaitingHello -> "Axion server handshake still pending."
                    is AxionServerConnection.State.Available -> "Installed Axion server plugin does not support this operation."
                    is AxionServerConnection.State.Unsupported -> "Axion client/server protocol mismatch."
                    AxionServerConnection.State.Disconnected -> AxionServerConnection.PLUGIN_REQUIRED_MESSAGE
                },
            )
            return
        }

        AxionServerConnection.clearStatusMessage(AxionServerConnection.PLUGIN_REQUIRED_MESSAGE)
        val requestId = AxionServerConnection.nextRequestId()
        AxionRequestTracker.register(requestId, AxionRequestTracker.RequestKind.Operation)
        AxionServerConnection.sendClientMessage(
            OperationBatchRequest(
                requestId = requestId,
                operations = remoteOperations,
                usesSymmetry = false,
                recordHistory = recordHistory,
            ),
        )
    }

    private fun flattenOperations(operation: EditOperation): List<EditOperation> {
        return when (operation) {
            is CompositeOperation -> operation.operations.flatMap(::flattenOperations)
            else -> listOf(operation)
        }
    }

    private fun toRemoteOperation(operation: EditOperation): AxionRemoteOperation? {
        return when (operation) {
            is ClearRegionOperation -> {
                val region = operation.region.normalized()
                ClearRegionRequest(
                    min = region.minCorner().toProtocolVector(),
                    max = region.maxCorner().toProtocolVector(),
                )
            }

            is CloneRegionOperation -> {
                val source = operation.sourceRegion.normalized()
                CloneRegionRequest(
                    sourceMin = source.minCorner().toProtocolVector(),
                    sourceMax = source.maxCorner().toProtocolVector(),
                    destinationOrigin = operation.destinationOrigin.toProtocolVector(),
                )
            }

            is CloneEntitiesOperation -> {
                val source = operation.sourceRegion.normalized()
                CloneEntitiesRequest(
                    sourceMin = source.minCorner().toProtocolVector(),
                    sourceMax = source.maxCorner().toProtocolVector(),
                    destinationOrigin = operation.destinationOrigin.toProtocolVector(),
                    rotationQuarterTurns = operation.rotationQuarterTurns,
                    mirrorAxis = when (operation.mirrorAxis) {
                        axion.common.operation.EntityMoveMirrorAxis.NONE -> PlacementMirrorAxisPayload.NONE
                        axion.common.operation.EntityMoveMirrorAxis.X -> PlacementMirrorAxisPayload.X
                        axion.common.operation.EntityMoveMirrorAxis.Z -> PlacementMirrorAxisPayload.Z
                    },
                )
            }

            is MoveEntitiesOperation -> {
                val source = operation.sourceRegion.normalized()
                MoveEntitiesRequest(
                    sourceMin = source.minCorner().toProtocolVector(),
                    sourceMax = source.maxCorner().toProtocolVector(),
                    destinationOrigin = operation.destinationOrigin.toProtocolVector(),
                    rotationQuarterTurns = operation.rotationQuarterTurns,
                    mirrorAxis = when (operation.mirrorAxis) {
                        axion.common.operation.EntityMoveMirrorAxis.NONE -> PlacementMirrorAxisPayload.NONE
                        axion.common.operation.EntityMoveMirrorAxis.X -> PlacementMirrorAxisPayload.X
                        axion.common.operation.EntityMoveMirrorAxis.Z -> PlacementMirrorAxisPayload.Z
                    },
                )
            }

            is StackRegionOperation -> {
                val source = operation.sourceRegion.normalized()
                StackRegionRequest(
                    sourceOrigin = source.minCorner().toProtocolVector(),
                    clipboardSize = operation.clipboardBuffer.size.toProtocolVector(),
                    cells = operation.clipboardBuffer.cells.map { it.toPayload() },
                    step = operation.step.toProtocolVector(),
                    repeatCount = operation.repeatCount,
                )
            }

            is SmearRegionOperation -> {
                val source = operation.sourceRegion.normalized()
                SmearRegionRequest(
                    sourceOrigin = source.minCorner().toProtocolVector(),
                    clipboardSize = operation.clipboardBuffer.size.toProtocolVector(),
                    cells = operation.clipboardBuffer.cells.map { it.toPayload() },
                    step = operation.step.toProtocolVector(),
                    repeatCount = operation.repeatCount,
                )
            }

            is ExtrudeOperation -> ExtrudeRequest(
                origin = operation.origin.toProtocolVector(),
                direction = operation.direction.vector.toProtocolVector(),
                expectedState = BlockArgumentParser.stringifyBlockState(operation.sourceState),
                mode = when (operation.mode) {
                    axion.common.operation.ExtrudeMode.EXTEND -> AxionExtrudeMode.EXTEND
                    axion.common.operation.ExtrudeMode.SHRINK -> AxionExtrudeMode.SHRINK
                },
                symmetry = null,
            )

            is SymmetryPlacementOperation -> PlaceBlocksRequest(
                placements = operation.placements.map { placement ->
                    PlacedBlockPayload(
                        pos = placement.pos.toProtocolVector(),
                        blockState = BlockArgumentParser.stringifyBlockState(placement.state),
                        blockEntityData = placement.blockEntityData?.nbt?.toString(),
                    )
                },
            )

            else -> null
        }
    }
}

private fun net.minecraft.util.math.BlockPos.toProtocolVector(): IntVector3 {
    return IntVector3(x, y, z)
}

private fun net.minecraft.util.math.Vec3i.toProtocolVector(): IntVector3 {
    return IntVector3(x, y, z)
}

private fun axion.common.model.ClipboardCell.toPayload(): ClipboardCellPayload {
    return ClipboardCellPayload(
        offset = offset.toProtocolVector(),
        blockState = BlockArgumentParser.stringifyBlockState(state),
        blockEntityData = blockEntityData?.nbt?.toString(),
    )
}

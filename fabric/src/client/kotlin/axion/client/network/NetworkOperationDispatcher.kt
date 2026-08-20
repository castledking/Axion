package axion.client.network

import axion.common.compat.VersionCompat
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneRegionOperation
import axion.client.history.HistoryManager
import axion.client.history.RemoteHistoryAdapter
import axion.client.compat.VersionCompatImpl
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import net.minecraft.client.MinecraftClient
import axion.common.operation.ExtrudeOperation
import axion.common.operation.FilteredCloneRegionOperation
import axion.common.operation.MoveEntitiesOperation
import axion.common.operation.OperationDispatcher
import axion.common.operation.SmearRegionOperation
import axion.common.operation.StackRegionOperation
import axion.common.operation.CloneEntitiesOperation
import axion.common.operation.DeleteEntitiesOperation
import axion.common.operation.SymmetryPlacementOperation
import axion.protocol.AxionExtrudeMode
import axion.protocol.AxionInteractionOrigin
import axion.protocol.AxionRemoteOperation
import axion.protocol.ClipboardCellPayload
import axion.protocol.ClearRegionRequest
import axion.protocol.CloneEntitiesRequest
import axion.protocol.CloneRegionRequest
import axion.protocol.DeleteEntitiesRequest
import axion.protocol.ExtrudeRequest
import axion.protocol.FilteredCloneRegionRequest
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
    private val interactionOrigin: AxionInteractionOrigin = AxionInteractionOrigin.NONE,
    /**
     * Read per dispatch rather than captured, because the No Updates capability
     * can be toggled between two dispatches from the same long-lived dispatcher.
     */
    private val suppressBlockUpdates: () -> Boolean = { true },
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
            val state = AxionServerConnection.state()
            val message = when (state) {
                is AxionServerConnection.State.AwaitingHello -> "Axion server handshake still pending."
                is AxionServerConnection.State.Available -> "Installed Axion server plugin does not support this operation."
                is AxionServerConnection.State.Unsupported -> "Axion client/server protocol mismatch."
                AxionServerConnection.State.Disconnected -> AxionServerConnection.PLUGIN_REQUIRED_MESSAGE
            }
            AxionServerConnection.notifyPlayerOnce(message)
            // Show action bar alert for Disconnected state (no plugin installed)
            if (state == AxionServerConnection.State.Disconnected) {
                VersionCompatImpl.notifyPlayer(
                    MinecraftClient.getInstance().player,
                    VersionCompatImpl.formatText(
                        VersionCompatImpl.createLiteral("⚠ Axion server plugin required for multiplayer editing"),
                        net.minecraft.util.Formatting.RED,
                    ),
                    true,
                )
            }
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
                interactionOrigin = interactionOrigin,
                suppressBlockUpdates = suppressBlockUpdates(),
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

            is FilteredCloneRegionOperation -> {
                val source = operation.sourceRegion.normalized()
                FilteredCloneRegionRequest(
                    sourceMin = source.minCorner().toProtocolVector(),
                    sourceMax = source.maxCorner().toProtocolVector(),
                    destinationOrigin = operation.destinationOrigin.toProtocolVector(),
                    copyAir = operation.copyAir,
                    keepExisting = operation.keepExisting,
                )
            }

            is CloneEntitiesOperation -> {
                val source = operation.sourceRegion.normalized()
                CloneEntitiesRequest(
                    entitySelection = operation.entitySelection,
                    sourceMin = source.minCorner().toProtocolVector(),
                    sourceMax = source.maxCorner().toProtocolVector(),
                    destinationOrigin = operation.destinationOrigin.toProtocolVector(),
                    rotationQuarterTurns = operation.rotationQuarterTurns,
                    mirrorAxis = when (operation.mirrorAxis) {
                        axion.common.operation.EntityMoveMirrorAxis.NONE -> PlacementMirrorAxisPayload.NONE
                        axion.common.operation.EntityMoveMirrorAxis.X -> PlacementMirrorAxisPayload.X
                        axion.common.operation.EntityMoveMirrorAxis.Y -> PlacementMirrorAxisPayload.Y
                        axion.common.operation.EntityMoveMirrorAxis.Z -> PlacementMirrorAxisPayload.Z
                    },
                )
            }

            is DeleteEntitiesOperation -> {
                val source = operation.sourceRegion.normalized()
                DeleteEntitiesRequest(
                    sourceMin = source.minCorner().toProtocolVector(),
                    sourceMax = source.maxCorner().toProtocolVector(),
                )
            }

            is MoveEntitiesOperation -> {
                val source = operation.sourceRegion.normalized()
                MoveEntitiesRequest(
                    entitySelection = operation.entitySelection,
                    sourceMin = source.minCorner().toProtocolVector(),
                    sourceMax = source.maxCorner().toProtocolVector(),
                    destinationOrigin = operation.destinationOrigin.toProtocolVector(),
                    rotationQuarterTurns = operation.rotationQuarterTurns,
                    mirrorAxis = when (operation.mirrorAxis) {
                        axion.common.operation.EntityMoveMirrorAxis.NONE -> PlacementMirrorAxisPayload.NONE
                        axion.common.operation.EntityMoveMirrorAxis.X -> PlacementMirrorAxisPayload.X
                        axion.common.operation.EntityMoveMirrorAxis.Y -> PlacementMirrorAxisPayload.Y
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
                    keepExisting = operation.keepExisting,
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
                direction = (VersionCompat.INSTANCE.directionGetVector(operation.direction) as net.minecraft.util.math.Vec3i).toProtocolVector(),
                expectedState = VersionCompat.INSTANCE.blockStateStringify(operation.sourceState),
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
                        blockState = VersionCompat.INSTANCE.blockStateStringify(placement.state),
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
        blockState = VersionCompat.INSTANCE.blockStateStringify(state),
        blockEntityData = blockEntityData?.nbt?.toString(),
    )
}

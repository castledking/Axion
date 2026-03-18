package axion.protocol

data class IntVector3(
    val x: Int,
    val y: Int,
    val z: Int,
)

data class DoubleVector3(
    val x: Double,
    val y: Double,
    val z: Double,
)

enum class AxionOperationType {
    CLEAR_REGION,
    CLONE_REGION,
    STACK_REGION,
    SMEAR_REGION,
    EXTRUDE,
    PLACE_BLOCKS,
}

enum class AxionResultCode {
    OK,
    WORLD_DISABLED,
    TOOL_DISABLED,
    PERMISSION_DENIED,
    OUTSIDE_ALLOWED_CUBOID,
    PROTECTED_REGION,
    WRITE_LIMIT_EXCEEDED,
    CLIPBOARD_LIMIT_EXCEEDED,
    REPEAT_LIMIT_EXCEEDED,
    EXTRUDE_LIMIT_EXCEEDED,
    UNDO_NOT_AVAILABLE,
    REDO_NOT_AVAILABLE,
    TRANSPORT_BUDGET_EXCEEDED,
    WORLD_MISMATCH,
    VALIDATION_FAILED,
}

enum class AxionResultSource {
    SERVER,
    REQUEST,
    POLICY,
    PERMISSION,
    CONFIG_CUBOID,
    WORLDGUARD,
    HISTORY,
    TRANSPORT,
}

sealed interface AxionClientMessage

data class ClientHello(
    val protocolVersion: Int,
    val clientVersion: String,
) : AxionClientMessage

data class OperationBatchRequest(
    val requestId: Long,
    val operations: List<AxionRemoteOperation>,
    val usesSymmetry: Boolean = false,
    val recordHistory: Boolean = true,
) : AxionClientMessage

data class UndoRequest(
    val requestId: Long,
    val transactionId: Long,
) : AxionClientMessage

data class RedoRequest(
    val requestId: Long,
    val transactionId: Long,
) : AxionClientMessage

data class NoClipStateRequest(
    val armed: Boolean,
) : AxionClientMessage

sealed interface AxionRemoteOperation {
    val type: AxionOperationType
}

data class ClearRegionRequest(
    val min: IntVector3,
    val max: IntVector3,
) : AxionRemoteOperation {
    override val type: AxionOperationType = AxionOperationType.CLEAR_REGION
}

data class CloneRegionRequest(
    val sourceMin: IntVector3,
    val sourceMax: IntVector3,
    val destinationOrigin: IntVector3,
) : AxionRemoteOperation {
    override val type: AxionOperationType = AxionOperationType.CLONE_REGION
}

data class ClipboardCellPayload(
    val offset: IntVector3,
    val blockState: String,
    val blockEntityData: String? = null,
)

data class PlacedBlockPayload(
    val pos: IntVector3,
    val blockState: String,
    val blockEntityData: String? = null,
)

data class StackRegionRequest(
    val sourceOrigin: IntVector3,
    val clipboardSize: IntVector3,
    val cells: List<ClipboardCellPayload>,
    val step: IntVector3,
    val repeatCount: Int,
) : AxionRemoteOperation {
    override val type: AxionOperationType = AxionOperationType.STACK_REGION
}

data class SmearRegionRequest(
    val sourceOrigin: IntVector3,
    val clipboardSize: IntVector3,
    val cells: List<ClipboardCellPayload>,
    val step: IntVector3,
    val repeatCount: Int,
) : AxionRemoteOperation {
    override val type: AxionOperationType = AxionOperationType.SMEAR_REGION
}

enum class AxionExtrudeMode {
    EXTEND,
    SHRINK,
}

data class SymmetryConfigPayload(
    val anchor: DoubleVector3,
    val rotationalEnabled: Boolean,
    val mirrorYEnabled: Boolean,
)

data class ExtrudeRequest(
    val origin: IntVector3,
    val direction: IntVector3,
    val expectedState: String,
    val mode: AxionExtrudeMode,
    val symmetry: SymmetryConfigPayload? = null,
) : AxionRemoteOperation {
    override val type: AxionOperationType = AxionOperationType.EXTRUDE
}

data class PlaceBlocksRequest(
    val placements: List<PlacedBlockPayload>,
) : AxionRemoteOperation {
    override val type: AxionOperationType = AxionOperationType.PLACE_BLOCKS
}

sealed interface AxionServerMessage

data class ServerHello(
    val protocolVersion: Int,
    val supportedOperations: Set<AxionOperationType>,
) : AxionServerMessage

data class OperationBatchResult(
    val requestId: Long,
    val accepted: Boolean,
    val message: String,
    val changedBlockCount: Int,
    val code: AxionResultCode = AxionResultCode.OK,
    val source: AxionResultSource = AxionResultSource.SERVER,
    val blockedPosition: IntVector3? = null,
    val transactionId: Long? = null,
    val actionLabel: String? = null,
    val changes: List<CommittedBlockChangePayload> = emptyList(),
) : AxionServerMessage

data class CommittedBlockChangePayload(
    val pos: IntVector3,
    val oldState: String,
    val newState: String,
    val oldBlockEntityData: String? = null,
    val newBlockEntityData: String? = null,
)

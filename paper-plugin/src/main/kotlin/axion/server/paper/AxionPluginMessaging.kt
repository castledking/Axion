package axion.server.paper

import axion.protocol.AxionClientMessage
import axion.protocol.AxionProtocol
import axion.protocol.AxionProtocolCodec
import axion.protocol.AxionTransportCodec
import axion.protocol.ClientHello
import axion.protocol.NoClipStateRequest
import axion.protocol.OperationBatchRequest
import axion.protocol.OperationBatchResult
import axion.protocol.RedoRequest
import axion.protocol.ServerHello
import axion.protocol.UndoRequest
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener

class AxionPluginMessaging(
    private val plugin: AxionPaperPlugin,
    private val policyService: AxionPolicyService,
    private val noClipService: AxionNoClipService,
) : PluginMessageListener {
    private val auditEnabled = plugin.config.getBoolean("audit.enabled", true)
    private val timingStats = AxionTimingStatsTracker(
        plugin = plugin,
        enabled = auditEnabled,
        summaryEvery = plugin.config.getInt("audit.summary-every", 50),
    )
    private val auditLogger = AxionAuditLogger(
        plugin = plugin,
        statsTracker = timingStats,
        enabled = auditEnabled,
        slowThresholdMillis = plugin.config.getLong("audit.slow-threshold-ms", 200L),
    )
    private val operationService = AxionOperationService(policyService)
    private var nextTransferId: Long = 1L

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != AxionProtocol.CHANNEL_ID) {
            return
        }

        val decoded = decodeClientMessage(message) ?: return
        when (decoded) {
            is ClientHello -> handleHello(player, decoded)
            is NoClipStateRequest -> handleNoClipState(player, decoded)
            is OperationBatchRequest -> handleOperationBatch(player, decoded)
            is UndoRequest -> handleUndo(player, decoded)
            is RedoRequest -> handleRedo(player, decoded)
        }
    }

    private fun decodeClientMessage(message: ByteArray): AxionClientMessage? {
        return if (AxionTransportCodec.isClientFrame(message)) {
            AxionClientMessageAssembler.consume(message)
        } else {
            AxionProtocolCodec.decodeClientMessage(message)
        }
    }

    private fun handleHello(player: Player, hello: ClientHello) {
        val response = ServerHello(
            protocolVersion = AxionProtocol.PROTOCOL_VERSION,
            supportedOperations = AxionOperationService.SUPPORTED_OPERATIONS,
        )
        sendMessage(player, response)
        if (hello.protocolVersion != AxionProtocol.PROTOCOL_VERSION) {
            plugin.logger.warning("Axion protocol mismatch from ${player.name}: client=${hello.protocolVersion}")
        }
    }

    private fun handleOperationBatch(player: Player, request: OperationBatchRequest) {
        val timing = AxionTimingContext()
        val result = operationService.applyBatch(player, request, timing)
        sendResult(
            player = player,
            result = result,
            actionLabel = result.actionLabel ?: operationActionLabel(request),
            operationSummary = request.operations.map { it.type.name }.distinct().joinToString(","),
            usesSymmetry = request.usesSymmetry,
            touchedBlockCount = request.operations.sumOf { operationService.estimatedTouchedCount(it) },
            plannedWriteCount = request.operations.sumOf { operationService.estimatedWriteCount(it) },
            timing = timing,
        )
    }

    private fun handleNoClipState(player: Player, request: NoClipStateRequest) {
        noClipService.setArmed(player, request.armed)
    }

    private fun handleUndo(player: Player, request: UndoRequest) {
        val timing = AxionTimingContext()
        val result = operationService.undo(player, request.requestId, request.transactionId, timing)
        sendResult(player, result, result.actionLabel ?: "Undo", "UNDO", false, result.changedBlockCount, result.changedBlockCount, timing)
    }

    private fun handleRedo(player: Player, request: RedoRequest) {
        val timing = AxionTimingContext()
        val result = operationService.redo(player, request.requestId, request.transactionId, timing)
        sendResult(player, result, result.actionLabel ?: "Redo", "REDO", false, result.changedBlockCount, result.changedBlockCount, timing)
    }

    private fun sendResult(
        player: Player,
        result: OperationBatchResult,
        actionLabel: String,
        operationSummary: String,
        usesSymmetry: Boolean,
        touchedBlockCount: Int,
        plannedWriteCount: Int,
        timing: AxionTimingContext,
    ) {
        try {
            timing.measureTransport {
                sendMessage(player, result)
            }
            if (result.accepted) {
                auditLogger.logAccepted(
                    player = player,
                    world = player.world,
                    actionLabel = actionLabel,
                    operationSummary = operationSummary,
                    usesSymmetry = usesSymmetry,
                    touchedBlockCount = touchedBlockCount,
                    plannedWriteCount = plannedWriteCount,
                    changedBlockCount = result.changedBlockCount,
                    transactionId = result.transactionId,
                    timing = timing.snapshot(),
                )
            } else {
                auditLogger.logDenied(
                    player = player,
                    world = player.world,
                    actionLabel = actionLabel,
                    operationSummary = operationSummary,
                    usesSymmetry = usesSymmetry,
                    touchedBlockCount = touchedBlockCount,
                    plannedWriteCount = plannedWriteCount,
                    rejection = AxionRejection(
                        code = result.code,
                        source = result.source,
                        message = result.message,
                        blockedPosition = result.blockedPosition,
                    ),
                    timing = timing.snapshot(),
                )
            }
        } catch (_: IllegalArgumentException) {
            timing.measureTransport { }
            auditLogger.logTransportDenied(
                player = player,
                world = player.world,
                actionLabel = actionLabel,
                operationSummary = operationSummary,
                usesSymmetry = usesSymmetry,
                touchedBlockCount = touchedBlockCount,
                plannedWriteCount = plannedWriteCount,
                timing = timing.snapshot(),
            )
            sendMessage(
                player,
                OperationBatchResult(
                    requestId = result.requestId,
                    accepted = false,
                    message = "Committed diff exceeded transport budget",
                    changedBlockCount = 0,
                    code = axion.protocol.AxionResultCode.TRANSPORT_BUDGET_EXCEEDED,
                    source = axion.protocol.AxionResultSource.TRANSPORT,
                ),
            )
        }
    }

    private fun operationActionLabel(request: OperationBatchRequest): String {
        val types = request.operations.map { it.type }.toSet()
        return when {
            types.contains(axion.protocol.AxionOperationType.CLONE_REGION) &&
                types.contains(axion.protocol.AxionOperationType.CLEAR_REGION) -> "Move"
            types.contains(axion.protocol.AxionOperationType.CLONE_REGION) -> "Clone"
            types.contains(axion.protocol.AxionOperationType.STACK_REGION) -> "Stack"
            types.contains(axion.protocol.AxionOperationType.SMEAR_REGION) -> "Smear"
            types.contains(axion.protocol.AxionOperationType.EXTRUDE) -> "Extrude"
            types.contains(axion.protocol.AxionOperationType.PLACE_BLOCKS) -> "Place"
            types == setOf(axion.protocol.AxionOperationType.CLEAR_REGION) -> "Erase"
            else -> "Edit"
        }
    }

    private fun sendMessage(player: Player, message: axion.protocol.AxionServerMessage) {
        AxionTransportCodec.encodeServerMessage(message, nextTransferId++)
            .forEach { payload ->
                player.sendPluginMessage(plugin, AxionProtocol.CHANNEL_ID, payload)
            }
    }

    fun logTimingSummary(reason: String) {
        timingStats.logSummary(reason)
    }

    fun resetTimingSummary() {
        timingStats.reset()
    }
}

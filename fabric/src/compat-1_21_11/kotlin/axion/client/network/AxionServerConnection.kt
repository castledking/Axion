package axion.client.network

import axion.AxionMod
import axion.client.compat.VersionCompatImpl
import axion.client.history.HistoryManager
import axion.client.history.RemoteHistoryAdapter
import axion.protocol.AxionClientMessage
import axion.protocol.AxionOperationType
import axion.protocol.AxionProtocol
import axion.protocol.AxionTransportCodec
import axion.protocol.ClientHello
import axion.protocol.ForcePlaceStateRequest
import axion.protocol.NoClipStateRequest
import axion.protocol.NoUpdatesStateRequest
import axion.protocol.OperationBatchResult
import axion.protocol.PhantomStateRequest
import axion.protocol.RedoRequest
import axion.protocol.ServerHello
import axion.protocol.UndoRequest
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Formatting

object AxionServerConnection {
    sealed interface State {
        data object Disconnected : State
        data object AwaitingHello : State
        data class Available(
            val protocolVersion: Int,
            val supportedOperations: Set<AxionOperationType>,
        ) : State

        data object Unsupported : State
    }

    private var state: State = State.Disconnected
    private var nextRequestId: Long = 1L
    private var nextTransferId: Long = 1L
    private var lastStatusMessage: String? = null
    private var lastSentNoClipArmed: Boolean? = null
    private var lastSentNoUpdatesArmed: Boolean? = null
    private var lastSentPhantomArmed: Boolean? = null
    private var lastSentForcePlaceArmed: Boolean? = null

    fun initialize() {
        VersionCompatImpl.registerAxionPayloadChannel(AxionPluginPayload.ID, AxionPluginPayload.CODEC)
        VersionCompatImpl.registerAxionReceiver(AxionPluginPayload.ID) { payload ->
            AxionServerMessageAssembler.consume(payload.bytes)?.let(::handleServerMessage)
        }

        VersionCompatImpl.onPlayJoin { client, _ ->
            if (VersionCompatImpl.hasLocalServer(client)) {
                state = State.Disconnected
                lastSentNoClipArmed = null
                lastSentNoUpdatesArmed = null
                lastSentPhantomArmed = null
                lastSentForcePlaceArmed = null
                nextTransferId = 1L
                return@onPlayJoin
            }

            state = State.AwaitingHello
            lastSentNoClipArmed = null
            lastSentNoUpdatesArmed = null
            lastSentPhantomArmed = null
            lastSentForcePlaceArmed = null
            nextTransferId = 1L
            send(
                ClientHello(
                    protocolVersion = AxionProtocol.PROTOCOL_VERSION,
                    clientVersion = VersionCompatImpl.getModVersion(AxionMod.MOD_ID),
                ),
            )
        }

        VersionCompatImpl.onPlayDisconnect { client ->
            state = State.Disconnected
            lastStatusMessage = null
            lastSentNoClipArmed = null
            lastSentNoUpdatesArmed = null
            lastSentPhantomArmed = null
            lastSentForcePlaceArmed = null
            nextTransferId = 1L
            AxionRequestTracker.clear()
            AxionServerMessageAssembler.clear()
            axion.client.render.ClientThreadCleanupScheduler.schedule(
                enqueueOnClientThread = { task -> VersionCompatImpl.runOnRenderThread(client, task) },
                cleanup = {
                    axion.client.render.AxionPreviewBufferCache.invalidate()
                    axion.client.render.AxionPreviewTemplateCache.invalidate()
                },
            )
        }
    }

    fun state(): State = state

    fun isRemoteAuthoritativeAvailable(): Boolean = state is State.Available

    fun supportsAll(operations: Set<AxionOperationType>): Boolean {
        return when (val current = state) {
            is State.Available -> current.supportedOperations.containsAll(operations)
            State.AwaitingHello -> true
            State.Disconnected,
            State.Unsupported,
                -> false
        }
    }

    fun nextRequestId(): Long = nextRequestId++

    fun notifyPlayerOnce(message: String) {
        if (lastStatusMessage == message) {
            return
        }

        lastStatusMessage = message
        VersionCompatImpl.notifyPlayer(MinecraftClient.getInstance().player, VersionCompatImpl.createLiteral(message), false)
    }

    fun clearStatusMessage(message: String) {
        if (lastStatusMessage == message) {
            lastStatusMessage = null
        }
    }

    fun sendClientMessage(message: AxionClientMessage) {
        AxionTransportCodec.encodeClientMessage(message, nextTransferId++)
            .forEach { payload -> VersionCompatImpl.sendAxionPayload(AxionPluginPayload(payload)) }
    }

    fun syncNoClipState(armed: Boolean) {
        when (state) {
            State.Disconnected,
            State.Unsupported,
                -> return

            State.AwaitingHello,
            is State.Available,
                -> Unit
        }

        if (lastSentNoClipArmed == armed) {
            return
        }

        lastSentNoClipArmed = armed
        sendClientMessage(
            NoClipStateRequest(armed = armed),
        )
    }

    fun syncNoUpdatesState(armed: Boolean) {
        when (state) {
            State.Disconnected,
            State.Unsupported,
                -> return

            State.AwaitingHello,
            is State.Available,
                -> Unit
        }

        if (lastSentNoUpdatesArmed == armed) {
            return
        }

        lastSentNoUpdatesArmed = armed
        sendClientMessage(
            NoUpdatesStateRequest(armed = armed),
        )
    }

    fun syncPhantomState(armed: Boolean) {
        when (state) {
            State.Disconnected,
            State.Unsupported,
                -> return

            State.AwaitingHello,
            is State.Available,
                -> Unit
        }

        if (lastSentPhantomArmed == armed) {
            return
        }

        lastSentPhantomArmed = armed
        sendClientMessage(
            PhantomStateRequest(armed = armed),
        )
    }

    fun syncForcePlaceState(armed: Boolean) {
        when (state) {
            State.Disconnected,
            State.Unsupported,
                -> return

            State.AwaitingHello,
            is State.Available,
                -> Unit
        }

        if (lastSentForcePlaceArmed == armed) {
            return
        }

        lastSentForcePlaceArmed = armed
        sendClientMessage(
            ForcePlaceStateRequest(armed = armed),
        )
    }

    fun onEndTick() {
        AxionServerMessageAssembler.evictExpired()
    }

    fun requestUndo(transactionId: Long) {
        val requestId = nextRequestId()
        AxionRequestTracker.register(requestId, AxionRequestTracker.RequestKind.Undo(transactionId))
        sendClientMessage(
            UndoRequest(
                requestId = requestId,
                transactionId = transactionId,
            ),
        )
    }

    fun requestRedo(transactionId: Long) {
        val requestId = nextRequestId()
        AxionRequestTracker.register(requestId, AxionRequestTracker.RequestKind.Redo(transactionId))
        sendClientMessage(
            RedoRequest(
                requestId = requestId,
                transactionId = transactionId,
            ),
        )
    }

    private fun send(message: ClientHello) {
        sendClientMessage(message)
    }

    private fun handleServerMessage(message: axion.protocol.AxionServerMessage) {
        when (message) {
            is ServerHello -> {
                state = if (message.protocolVersion == AxionProtocol.PROTOCOL_VERSION) {
                    clearStatusMessage(PLUGIN_REQUIRED_MESSAGE)
                    State.Available(message.protocolVersion, message.supportedOperations)
                } else {
                    notifyPlayerOnce(VERSION_MISMATCH_MESSAGE)
                    State.Unsupported
                }
            }

            is OperationBatchResult -> {
                if (state == State.AwaitingHello) {
                    state = State.Available(
                        protocolVersion = AxionProtocol.PROTOCOL_VERSION,
                        supportedOperations = AxionOperationType.entries.toSet(),
                    )
                }
                val requestKind = AxionRequestTracker.complete(message.requestId)
                if (!message.accepted) {
                    if (message.source == axion.protocol.AxionResultSource.GRIEF_PREVENTION) {
                        VersionCompatImpl.notifyPlayer(
                            MinecraftClient.getInstance().player,
                            VersionCompatImpl.formatText(
                                VersionCompatImpl.createLiteral("Couldn't apply edit, try again."),
                                Formatting.RED,
                            ),
                            true,
                        )
                    } else {
                        notifyPlayerOnce("Axion server rejected edit [${message.code.name}]: ${message.message}")
                    }
                } else {
                    clearStatusMessage("Axion server rejected edit [${message.code.name}]: ${message.message}")
                    val entry = RemoteHistoryAdapter.toHistoryEntry(message)
                    when (requestKind) {
                        is AxionRequestTracker.RequestKind.Operation,
                        null,
                            -> entry?.let(HistoryManager::record)

                        is AxionRequestTracker.RequestKind.Undo ->
                            HistoryManager.applyRemoteUndo(requestKind.transactionId, entry)

                        is AxionRequestTracker.RequestKind.Redo ->
                            HistoryManager.applyRemoteRedo(requestKind.transactionId, entry)
                    }
                }
            }
        }
    }

    const val PLUGIN_REQUIRED_MESSAGE: String = "Axion server plugin required for multiplayer editing."
    private const val VERSION_MISMATCH_MESSAGE: String = "Axion client/server protocol mismatch."
}

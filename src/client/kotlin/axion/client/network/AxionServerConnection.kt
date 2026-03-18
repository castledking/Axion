package axion.client.network

import axion.AxionMod
import axion.client.history.HistoryManager
import axion.client.history.RemoteHistoryAdapter
import axion.protocol.AxionClientMessage
import axion.protocol.AxionOperationType
import axion.protocol.AxionProtocol
import axion.protocol.AxionTransportCodec
import axion.protocol.ClientHello
import axion.protocol.NoClipStateRequest
import axion.protocol.OperationBatchResult
import axion.protocol.RedoRequest
import axion.protocol.ServerHello
import axion.protocol.UndoRequest
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

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

    fun initialize() {
        PayloadTypeRegistry.playC2S().register(AxionPluginPayload.ID, AxionPluginPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(AxionPluginPayload.ID, AxionPluginPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(AxionPluginPayload.ID) { payload, context ->
            context.client().execute {
                AxionServerMessageAssembler.consume(payload.bytes)?.let(::handleServerMessage)
            }
        }

        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, client ->
            if (client.server != null) {
                state = State.Disconnected
                lastSentNoClipArmed = null
                nextTransferId = 1L
                return@Join
            }

            state = State.AwaitingHello
            lastSentNoClipArmed = null
            nextTransferId = 1L
            send(
                ClientHello(
                    protocolVersion = AxionProtocol.PROTOCOL_VERSION,
                    clientVersion = FabricLoader.getInstance()
                        .getModContainer(AxionMod.MOD_ID)
                        .orElseThrow()
                        .metadata
                        .version
                        .friendlyString,
                ),
            )
        })

        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, client ->
            state = State.Disconnected
            lastStatusMessage = null
            lastSentNoClipArmed = null
            nextTransferId = 1L
            AxionRequestTracker.clear()
            AxionServerMessageAssembler.clear()
            client.execute { }
        })
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
        MinecraftClient.getInstance().player?.sendMessage(Text.literal(message), false)
    }

    fun clearStatusMessage(message: String) {
        if (lastStatusMessage == message) {
            lastStatusMessage = null
        }
    }

    fun sendClientMessage(message: AxionClientMessage) {
        AxionTransportCodec.encodeClientMessage(message, nextTransferId++)
            .forEach { payload ->
                ClientPlayNetworking.send(AxionPluginPayload(payload))
            }
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
                    notifyPlayerOnce("Axion server rejected edit [${message.code.name}]: ${message.message}")
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

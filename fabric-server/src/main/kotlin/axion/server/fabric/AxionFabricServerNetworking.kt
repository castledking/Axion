package axion.server.fabric

import axion.protocol.AxionClientMessage
import axion.protocol.AxionProtocol
import axion.protocol.AxionProtocolCodec
import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.AxionServerMessage
import axion.protocol.AxionTransportCodec
import axion.protocol.ClientHello
import axion.protocol.FlightSpeedRequest
import axion.protocol.NoClipStateRequest
import axion.protocol.OperationBatchRequest
import axion.protocol.OperationBatchResult
import axion.protocol.RedoRequest
import axion.protocol.ServerHello
import axion.protocol.UndoRequest
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.Logger

class AxionFabricServerNetworking(
    private val logger: Logger,
    private val noClipService: AxionFabricNoClipService,
) {
    private var nextTransferId: Long = 1L
    private val history = AxionFabricServerHistory()
    private val operationService = AxionFabricOperationService(history)
    private val historyActionService = AxionFabricHistoryActionService(history)

    fun initialize() {
        PayloadTypeRegistry.playC2S().register(AxionServerPayload.ID, AxionServerPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(AxionServerPayload.ID, AxionServerPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(AxionServerPayload.ID) { payload, context ->
            context.server().execute {
                val decoded = decodeClientMessage(payload.bytes) ?: return@execute
                when (decoded) {
                    is ClientHello -> handleHello(context.player(), decoded)
                    is NoClipStateRequest -> noClipService.setArmed(context.player(), decoded.armed)
                    is FlightSpeedRequest -> handleFlightSpeed(context.player(), decoded)
                    is OperationBatchRequest -> handleOperationBatch(context.player(), decoded)
                    is UndoRequest -> handleUndo(context.server(), context.player(), decoded)
                    is RedoRequest -> handleRedo(context.server(), context.player(), decoded)
                }
            }
        }

        ServerPlayConnectionEvents.DISCONNECT.register(ServerPlayConnectionEvents.Disconnect { handler, _ ->
            noClipService.clear(handler.player)
        })
    }

    fun stop(server: MinecraftServer) {
        noClipService.stop(server)
    }

    private fun decodeClientMessage(message: ByteArray): AxionClientMessage? {
        return if (AxionTransportCodec.isClientFrame(message)) {
            AxionClientMessageAssembler.consume(message)
        } else {
            AxionProtocolCodec.decodeClientMessage(message)
        }
    }

    private fun handleHello(player: ServerPlayerEntity, hello: ClientHello) {
        sendMessage(
            player,
            ServerHello(
                protocolVersion = AxionProtocol.PROTOCOL_VERSION,
                supportedOperations = AxionFabricOperationService.SUPPORTED_OPERATIONS,
            ),
        )
        if (hello.protocolVersion != AxionProtocol.PROTOCOL_VERSION) {
            logger.warn("Axion Fabric server protocol mismatch from {}: client={}", player.gameProfile.name, hello.protocolVersion)
        }
    }

    private fun handleOperationBatch(player: ServerPlayerEntity, request: OperationBatchRequest) {
        sendMessage(player, operationService.applyBatch(player, request))
    }

    private fun handleUndo(server: MinecraftServer, player: ServerPlayerEntity, request: UndoRequest) {
        sendMessage(player, historyActionService.undo(server, player, request.requestId, request.transactionId))
    }

    private fun handleRedo(server: MinecraftServer, player: ServerPlayerEntity, request: RedoRequest) {
        sendMessage(player, historyActionService.redo(server, player, request.requestId, request.transactionId))
    }

    private fun handleFlightSpeed(player: ServerPlayerEntity, request: FlightSpeedRequest) {
        // For Fabric server, we just apply the flight speed directly to the player
        // The noPhysics flag helps prevent rubberbanding at high speeds (>500%)
        val vanillaFlySpeed = 0.05f
        val targetSpeed = vanillaFlySpeed * request.multiplier

        if (player.abilities.flySpeed != targetSpeed) {
            player.abilities.flySpeed = targetSpeed

            // For high speeds, enable noPhysics to prevent rubberbanding
            if (request.multiplier >= 5.0f) {
                player.noClip = true
            } else {
                // Only disable noClip if NoClip service hasn't enabled it
                if (!noClipService.isEnabled(player)) {
                    player.noClip = false
                }
            }
        }
    }

    private fun sendRejected(
        player: ServerPlayerEntity,
        requestId: Long,
        code: AxionResultCode = AxionResultCode.TOOL_DISABLED,
        source: AxionResultSource = AxionResultSource.REQUEST,
        message: String,
    ) {
        sendMessage(
            player,
            OperationBatchResult(
                requestId = requestId,
                accepted = false,
                message = message,
                changedBlockCount = 0,
                code = code,
                source = source,
            ),
        )
    }

    private fun sendMessage(player: ServerPlayerEntity, message: AxionServerMessage) {
        AxionTransportCodec.encodeServerMessage(message, nextTransferId++)
            .forEach { payload ->
                ServerPlayNetworking.send(player, AxionServerPayload(payload))
            }
    }
}

package axion.client.network

import axion.AxionMod
import axion.client.compat.VersionCompatImpl
import axion.protocol.AxionProtocol
import axion.protocol.OperationAck
import axion.protocol.ServerClipboardResponse
import axion.protocol.ServerHistoryResponse
import axion.protocol.UndoRequest
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import java.util.concurrent.ConcurrentHashMap

/**
 * Server connection handler for 1.20.6
 * Handles networking with PacketByteBuf-based payloads
 */
object AxionServerConnection {
    private var isConnected = false
    private var serverProtocolVersion: Int = -1
    private var canUseDirectWrites: Boolean = false
    private var canUseNoClip: Boolean = false
    private val pendingOperations = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private var lastSentNoClipArmed: Boolean? = null

    fun initialize() {
        // Initialize the version compatibility layer
        VersionCompat.initialize(VersionCompatImpl)
        
        PayloadTypeRegistry.playC2S().register(AxionPluginPayload.ID, AxionPluginPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(AxionPluginPayload.ID, AxionPluginPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(AxionPluginPayload.ID) { payload, context ->
            context.client().execute {
                val buf = io.netty.buffer.Unpooled.wrappedBuffer(payload.bytes)
                val packetBuf = net.minecraft.network.PacketByteBuf(buf)
                try {
                    AxionProtocol.decode(packetBuf) { message ->
                        handleMessage(message)
                    }
                } catch (e: Exception) {
                    AxionMod.LOGGER.error("Failed to decode Axion packet", e)
                }
            }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            isConnected = true
            AxionMod.LOGGER.info("Connected to server, checking Axion plugin support...")
            sendHandshake()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            isConnected = false
            serverProtocolVersion = -1
            canUseDirectWrites = false
            canUseNoClip = false
        }
    }

    private fun sendHandshake() {
        val buf = io.netty.buffer.Unpooled.buffer()
        val packetBuf = net.minecraft.network.PacketByteBuf(buf)
        AxionProtocol.writeHandshake(packetBuf)
        send(packetBuf)
    }

    fun send(buf: net.minecraft.network.PacketByteBuf) {
        if (!isConnected) return
        val bytes = ByteArray(buf.readableBytes())
        buf.readBytes(bytes)
        ClientPlayNetworking.send(AxionPluginPayload(bytes))
    }

    fun isConnected(): Boolean = isConnected && serverProtocolVersion >= 0
    fun canUseDirectWrites(): Boolean = canUseDirectWrites
    fun canUseNoClip(): Boolean = canUseNoClip

    private fun handleMessage(message: Any) {
        when (message) {
            is AxionProtocol.HandshakeResponse -> {
                serverProtocolVersion = message.protocolVersion
                canUseDirectWrites = message.supportsDirectWrite
                canUseNoClip = message.supportsNoClip
                AxionMod.LOGGER.info(
                    "Axion server connected (protocol={}, directWrites={}, noClip={})",
                    serverProtocolVersion, canUseDirectWrites, canUseNoClip
                )
            }
            is OperationAck -> {
                pendingOperations.remove(message.operationId)?.invoke(message.success)
            }
            is ServerClipboardResponse -> {
                // Handle clipboard response
            }
            is ServerHistoryResponse -> {
                // Handle history response
            }
        }
    }

    fun registerPendingOperation(id: String, callback: (Boolean) -> Unit) {
        pendingOperations[id] = callback
    }
}

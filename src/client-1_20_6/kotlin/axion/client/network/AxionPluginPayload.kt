package axion.client.network

import axion.protocol.AxionProtocol
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Custom payload for 1.20.6 networking (uses PacketByteBuf instead of RegistryByteBuf)
 */
class AxionPluginPayload(val bytes: ByteArray) : CustomPayload {
    companion object {
        val ID: CustomPayload.Id<AxionPluginPayload> = CustomPayload.Id(
            Identifier(
                AxionProtocol.CHANNEL_ID.substringBefore(':'),
                AxionProtocol.CHANNEL_ID.substringAfter(':')
            )
        )

        // 1.20.6 uses PacketByteBuf instead of RegistryByteBuf
        val CODEC = CustomPayload.codec<AxionPluginPayload>(
            { value, buf -> buf.writeBytes(value.bytes) },
            { buf ->
                val bytes = ByteArray(buf.readableBytes())
                buf.readBytes(bytes)
                AxionPluginPayload(bytes)
            }
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}

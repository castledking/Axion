package axion.client.network

import axion.protocol.AxionProtocol
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

data class AxionPluginPayload(
    val bytes: ByteArray,
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<AxionPluginPayload> = CustomPayload.Id(
            Identifier.of(AxionProtocol.CHANNEL_ID.substringBefore(':'), AxionProtocol.CHANNEL_ID.substringAfter(':')),
        )

        val CODEC: PacketCodec<RegistryByteBuf, AxionPluginPayload> = PacketCodec.of(
            { value, buf -> buf.writeBytes(value.bytes) },
            { buf ->
                val bytes = ByteArray(buf.readableBytes())
                buf.readBytes(bytes)
                AxionPluginPayload(bytes)
            },
        )
    }
}

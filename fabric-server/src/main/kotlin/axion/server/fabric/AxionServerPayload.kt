package axion.server.fabric

import axion.protocol.AxionProtocol
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

data class AxionServerPayload(
    val bytes: ByteArray,
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<AxionServerPayload> = CustomPayload.Id(
            Identifier.of(
                AxionProtocol.CHANNEL_ID.substringBefore(':'),
                AxionProtocol.CHANNEL_ID.substringAfter(':'),
            ),
        )

        val CODEC: PacketCodec<RegistryByteBuf, AxionServerPayload> = PacketCodec.of(
            { value, buf -> buf.writeBytes(value.bytes) },
            { buf ->
                val bytes = ByteArray(buf.readableBytes())
                buf.readBytes(bytes)
                AxionServerPayload(bytes)
            },
        )
    }
}

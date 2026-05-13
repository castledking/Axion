package axion.client.network

import axion.common.compat.VersionCompat
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
            VersionCompat.INSTANCE.identifierOf(AxionProtocol.CHANNEL_ID.substringBefore(':'), AxionProtocol.CHANNEL_ID.substringAfter(':')),
        )

        @Suppress("UNCHECKED_CAST")
        val CODEC: PacketCodec<RegistryByteBuf, AxionPluginPayload> =
            VersionCompat.INSTANCE.createAxionPluginPayloadCodec() as PacketCodec<RegistryByteBuf, AxionPluginPayload>
    }
}

package axion.client.network

import axion.common.compat.VersionCompat
import axion.protocol.AxionProtocol
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data class AxionPluginPayload(
    val bytes: ByteArray,
) : CustomPacketPayload {
    override fun getId(): CustomPacketPayload.Id<out CustomPacketPayload> = ID

    companion object {
        val ID: CustomPacketPayload.Id<AxionPluginPayload> = CustomPacketPayload.Id(
            VersionCompat.INSTANCE.identifierOf(AxionProtocol.CHANNEL_ID.substringBefore(':'), AxionProtocol.CHANNEL_ID.substringAfter(':')),
        )

        @Suppress("UNCHECKED_CAST")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AxionPluginPayload> =
            VersionCompat.INSTANCE.createAxionPluginPayloadCodec() as StreamCodec<RegistryFriendlyByteBuf, AxionPluginPayload>
    }
}

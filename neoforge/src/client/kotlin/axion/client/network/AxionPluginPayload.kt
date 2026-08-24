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
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    companion object {
        val ID: CustomPacketPayload.Type<AxionPluginPayload> = CustomPacketPayload.createType(
            AxionProtocol.CHANNEL_ID,

        )

        @Suppress("UNCHECKED_CAST")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AxionPluginPayload> =
            VersionCompat.INSTANCE.createAxionPluginPayloadCodec() as StreamCodec<RegistryFriendlyByteBuf, AxionPluginPayload>
    }
}

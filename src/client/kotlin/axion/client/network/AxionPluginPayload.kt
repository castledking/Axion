package axion.client.network

import axion.common.compat.VersionCompat
import axion.protocol.AxionProtocol
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.lang.reflect.Proxy

data class AxionPluginPayload(
    val bytes: ByteArray,
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<AxionPluginPayload> = CustomPayload.Id(
            VersionCompat.INSTANCE.identifierOf(AxionProtocol.CHANNEL_ID.substringBefore(':'), AxionProtocol.CHANNEL_ID.substringAfter(':')),
        )

        val CODEC: PacketCodec<RegistryByteBuf, AxionPluginPayload> = createAxionPluginPayloadCodec()
    }
}

@Suppress("UNCHECKED_CAST")
fun createAxionPluginPayloadCodec(): PacketCodec<RegistryByteBuf, AxionPluginPayload> {
    val codecClass = PacketCodec::class.java
    codecClass.methods.firstOrNull { it.name == "ofStatic" && it.parameterCount == 2 }?.let { method ->
        return method.invoke(
            null,
            packetEncoder(method.parameterTypes[0], valueFirst = false),
            packetDecoder(method.parameterTypes[1]),
        ) as PacketCodec<RegistryByteBuf, AxionPluginPayload>
    }
    codecClass.methods.firstOrNull { it.name == "ofMember" && it.parameterCount == 2 }?.let { method ->
        return method.invoke(
            null,
            packetEncoder(method.parameterTypes[0], valueFirst = true),
            packetDecoder(method.parameterTypes[1]),
        ) as PacketCodec<RegistryByteBuf, AxionPluginPayload>
    }
    val method = codecClass.methods.first { it.name == "of" && it.parameterCount == 2 }
    val encoderType = method.parameterTypes[0]
    return method.invoke(
        null,
        packetEncoder(encoderType, valueFirst = encoderType.simpleName.contains("ValueFirst")),
        packetDecoder(method.parameterTypes[1]),
    ) as PacketCodec<RegistryByteBuf, AxionPluginPayload>
}

private fun packetEncoder(type: Class<*>, valueFirst: Boolean): Any =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
        if (method.name == "encode" && args != null && args.size == 2) {
            val payload = args[if (valueFirst) 0 else 1] as AxionPluginPayload
            val buf = args[if (valueFirst) 1 else 0] as RegistryByteBuf
            buf.writeBytes(payload.bytes)
        }
        null
    }

private fun packetDecoder(type: Class<*>): Any =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
        if (method.name == "decode" && args != null && args.size == 1) {
            val buf = args[0] as RegistryByteBuf
            val bytes = ByteArray(buf.readableBytes())
            buf.readBytes(bytes)
            AxionPluginPayload(bytes)
        } else {
            null
        }
    }

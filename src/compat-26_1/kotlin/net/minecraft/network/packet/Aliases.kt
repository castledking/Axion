package net.minecraft.network.packet

interface CustomPayload : net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    fun getId(): Id<out CustomPayload>

    override fun type(): net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<out net.minecraft.network.protocol.common.custom.CustomPacketPayload> {
        return getId().delegate
    }

    class Id<T : CustomPayload>(
        id: net.minecraft.resources.Identifier,
    ) {
        internal val delegate: net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> =
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type(id)
    }
}

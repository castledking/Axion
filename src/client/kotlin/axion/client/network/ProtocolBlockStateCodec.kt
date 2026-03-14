package axion.client.network

import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.registry.RegistryKeys

object ProtocolBlockStateCodec {
    fun decode(state: String): BlockState? {
        val world = MinecraftClient.getInstance().world ?: return null
        return BlockArgumentParser.block(
            world.registryManager.getOrThrow(RegistryKeys.BLOCK),
            state,
            false,
        ).blockState()
    }
}

package axion.client.history

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.server.IntegratedServer
import net.minecraft.client.world.ClientWorld
import net.minecraft.resources.ResourceKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.World

val MinecraftClient.world
    get() = level

val MinecraftClient.server: IntegratedServer?
    get() = getSingleplayerServer()

val ClientWorld.registryKey: ResourceKey<World>
    get() = dimension()

fun IntegratedServer.getWorld(key: ResourceKey<World>?): ServerWorld? {
    return key?.let(::getLevel)
}

val Block.defaultState: BlockState
    get() = defaultBlockState()

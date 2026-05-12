package axion.client.network

import net.minecraft.client.MinecraftClient

val MinecraftClient.world
    get() = level

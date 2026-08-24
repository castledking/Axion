package axion.mixin.compat

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

val MinecraftClient.currentScreen: net.minecraft.client.gui.screens.Screen?
    get() = screen

fun currentScreenOf(client: MinecraftClient): Screen? = client.screen as? Screen

val MinecraftClient.interactionManager
    get() = gameMode

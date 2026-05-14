package axion.mixin.compat

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

fun currentScreenOf(client: MinecraftClient): Screen? = client.currentScreen

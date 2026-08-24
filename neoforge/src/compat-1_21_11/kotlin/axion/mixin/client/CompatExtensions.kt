package axion.mixin.compat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

fun currentScreenOf(client: Minecraft): Screen? = minecraft.screen

package axion.client.input

import com.mojang.blaze3d.platform.Window
import net.minecraft.client.MinecraftClient
import net.minecraft.client.Options
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.option.KeyBinding

val MinecraftClient.currentScreen: Screen?
    get() = screen as? Screen

val MinecraftClient.interactionManager
    get() = gameMode

val MinecraftClient.world
    get() = level

val MinecraftClient.inGameHud: Gui
    get() = gui

val Options.attackKey: KeyBinding
    get() = keyAttack

val Options.useKey: KeyBinding
    get() = keyUse

fun MultiPlayerGameMode.cancelBlockBreaking() {
    stopDestroyBlock()
}

fun KeyBinding.wasPressed(): Boolean = consumeClick()

val KeyBinding.isPressed: Boolean
    get() = isDown

val Window.handle: Long
    get() = handle()

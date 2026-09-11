package axion.client.input

import com.mojang.blaze3d.platform.Window
import net.minecraft.client.MinecraftClient
import net.minecraft.client.Options
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.option.KeyBinding

val MinecraftClient.currentScreen: net.minecraft.client.gui.screens.Screen?
    get() = gui.screen()

val MinecraftClient.interactionManager
    get() = gameMode

val MinecraftClient.world
    get() = level

// 26.2 split the old Gui in two: Gui is now the screen manager and Hud is the
// HUD that Gui owns, so setOverlayMessage lives on gui.hud.
val MinecraftClient.inGameHud: net.minecraft.client.gui.Hud
    get() = gui.hud

val Options.attackKey: KeyBinding
    get() = keyAttack

val Options.useKey: KeyBinding
    get() = keyUse

val Options.swapHandsKey: KeyBinding
    get() = keySwapOffhand

fun MultiPlayerGameMode.cancelBlockBreaking() {
    stopDestroyBlock()
}

fun KeyBinding.wasPressed(): Boolean = consumeClick()

val KeyBinding.isPressed: Boolean
    get() = isDown

val Window.handle: Long
    get() = handle()

/**
 * 26.2 moved screen management off Minecraft onto Gui. `setScreenAndShow` also
 * exists but forces a frame render — it is the load/disconnect-boundary
 * variant — so ordinary screen switching goes through `gui.setScreen`.
 */
fun MinecraftClient.setScreen(screen: net.minecraft.client.gui.screens.Screen?) {
    gui.setScreen(screen)
}

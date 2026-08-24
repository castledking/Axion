package axion.client.editor

import com.mojang.blaze3d.platform.Window
import net.minecraft.client.MinecraftClient
import net.minecraft.client.MouseHandler
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.Options
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Vec3d

val MinecraftClient.currentScreen: net.minecraft.client.gui.screens.Screen?
    get() = screen

val MinecraftClient.world
    get() = level

val MinecraftClient.inGameHud
    get() = gui

val MinecraftClient.isWindowFocused: Boolean
    get() = isWindowActive()

val MinecraftClient.mouse: MouseHandler
    get() = mouseHandler

val Window.handle: Long
    get() = handle()

val Window.scaledWidth: Int
    get() = guiScaledWidth

val Window.scaledHeight: Int
    get() = guiScaledHeight

val Window.width: Int
    get() = screenWidth

val Window.height: Int
    get() = screenHeight

val PlayerEntity.isInCreativeMode: Boolean
    get() = isCreative

// The editor controller speaks yarn's lockCursor/unlockCursor vocabulary;
// MouseHandler names the same operations grabMouse/releaseMouse.
fun MouseHandler.lockCursor() {
    grabMouse()
}

fun MouseHandler.unlockCursor() {
    releaseMouse()
}

// Editor flight input, yarn vocabulary.
val Options.forwardKey: KeyBinding
    get() = keyUp

val Options.backKey: KeyBinding
    get() = keyDown

val Options.leftKey: KeyBinding
    get() = keyLeft

val Options.rightKey: KeyBinding
    get() = keyRight

val Options.jumpKey: KeyBinding
    get() = keyJump

val Options.sneakKey: KeyBinding
    get() = keyShift

val Options.sprintKey: KeyBinding
    get() = keySprint

val KeyBinding.isPressed: Boolean
    get() = isDown

// Accessor-based ability speeds on 26.x; yarn exposes a flySpeed field.
val net.minecraft.world.entity.player.Abilities.flySpeed: Float
    get() = flyingSpeed

var Entity.yaw: Float
    get() = yRot
    set(value) {
        yRot = value
    }

var Entity.pitch: Float
    get() = xRot
    set(value) {
        xRot = value
    }

fun Entity.getRotationVec(tickDelta: Float): Vec3d = getViewVector(tickDelta)

fun Entity.setVelocity(velocity: Vec3d) {
    setDeltaMovement(velocity)
}

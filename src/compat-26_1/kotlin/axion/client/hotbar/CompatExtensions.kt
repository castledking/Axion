package axion.client.hotbar

import com.mojang.blaze3d.platform.Window
import net.minecraft.client.MinecraftClient
import net.minecraft.client.Mouse
import net.minecraft.client.Options
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.world.ClientWorld
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.screen.Screen
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Abilities
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i

val MinecraftClient.currentScreen: Screen?
    get() = screen as? Screen

val MinecraftClient.world
    get() = level

val MinecraftClient.interactionManager
    get() = gameMode

val ClientWorld.registryManager: DynamicRegistryManager
    get() = registryAccess()

fun PlayerInventory.getStack(slot: Int): ItemStack = getItem(slot)

fun PlayerInventory.setStack(slot: Int, stack: ItemStack) {
    setItem(slot, stack)
    setChanged()
}

fun MultiPlayerGameMode.clickCreativeStack(stack: ItemStack, slot: Int) {
    handleCreativeModeItemAdd(stack, slot)
}

val Item.defaultStack: ItemStack
    get() = defaultInstance

val MinecraftClient.textRenderer: TextRenderer
    get() = font

val Options.hudHidden: Boolean
    get() = hideGui

val Window.scaledWidth: Int
    get() = guiScaledWidth

val Window.scaledHeight: Int
    get() = guiScaledHeight

val Mouse.isCursorLocked: Boolean
    get() = isMouseGrabbed

fun Mouse.unlockCursor() {
    releaseMouse()
}

fun Mouse.lockCursor() {
    grabMouse()
}

val MinecraftClient.mouse: Mouse
    get() = mouseHandler

val Abilities.allowFlying: Boolean
    get() = mayfly

val TextRenderer.fontHeight: Int
    get() = lineHeight

fun TextRenderer.getWidth(text: String): Int = width(text)

fun TextRenderer.getWidth(text: Component): Int = width(text)

val Options.mainArm
    get() = mainHand()

val net.minecraft.client.OptionInstance<HumanoidArm>.value: HumanoidArm
    get() = get()

val Direction.vector: Vec3i
    get() = Vec3i(unitVec3i.x, unitVec3i.y, unitVec3i.z)

fun MinecraftClient.scheduleStop() {
    stop()
}

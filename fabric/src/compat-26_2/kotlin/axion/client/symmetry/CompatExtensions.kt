package axion.client.symmetry

import net.minecraft.client.MinecraftClient

import net.minecraft.client.Minecraft
import net.minecraft.world.level.block.Block
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

val Minecraft.world get() = level
val Minecraft.crosshairTarget get() = hitResult
// 26.2 split the old Gui in two: Gui is now the screen manager and Hud is the
// HUD that Gui owns, so setOverlayMessage lives on gui.hud.
val Minecraft.inGameHud get() = gui.hud
val Minecraft.server get() = singleplayerServer

val Direction.vector: Vec3i get() = unitVec3i
val Direction.offsetX: Int get() = stepX
val Direction.offsetY: Int get() = stepY
val Direction.offsetZ: Int get() = stepZ

val Entity.rotationVecClient: Vec3 get() = getViewVector(1.0f)

fun directionGetFacing(vec: Vec3): Direction = Direction.getApproximateNearest(vec)

val Block.defaultState: BlockState
    get() = defaultBlockState()

fun Player.sendMessage(message: net.minecraft.network.chat.Component, actionBar: Boolean) {
    if (actionBar) (this as? net.minecraft.client.player.LocalPlayer)?.sendOverlayMessage(message) ?: sendSystemMessage(message)
    else sendSystemMessage(message)
}

fun LivingEntity.swingHand(hand: InteractionHand) = swing(hand)

fun LivingEntity.getStackInHand(hand: InteractionHand): ItemStack = getItemInHand(hand)

val BlockState.soundGroup get() = soundType

fun net.minecraft.world.level.Level.isInBuildLimit(pos: net.minecraft.core.BlockPos): Boolean =
    isInsideBuildHeight(pos)

val net.minecraft.world.level.Level.registryKey get() = dimension()

fun net.minecraft.core.BlockPos.toImmutable(): net.minecraft.core.BlockPos = immutable()

fun net.minecraft.server.MinecraftServer.execute(task: Runnable) = submit(task)

/** 26.2 moved the toast manager off Minecraft onto Gui. */
val MinecraftClient.toastManager
    get() = gui.toastManager()

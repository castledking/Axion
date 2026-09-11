package axion.client.mode

import net.minecraft.client.MinecraftClient
import net.minecraft.client.Options
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.world.ClientWorld
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.*
import net.minecraft.client.server.IntegratedServer
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.RaycastContext
import net.minecraft.world.entity.player.Abilities
import net.minecraft.world.phys.AABB
import java.util.UUID

val MinecraftClient.currentScreen: net.minecraft.client.gui.screens.Screen?
    get() = gui.screen()

val MinecraftClient.world
    get() = level

val MinecraftClient.interactionManager
    get() = gameMode

val MinecraftClient.server: IntegratedServer?
    get() = getSingleplayerServer()

val IntegratedServer.playerManager
    get() = getPlayerList()

val MinecraftClient.crosshairTarget: HitResult?
    get() = hitResult

// 26.2 split the old Gui in two: Gui is now the screen manager and Hud is the
// HUD that Gui owns, so setOverlayMessage lives on gui.hud.
val MinecraftClient.inGameHud
    get() = gui.hud

val Options.attackKey: KeyBinding
    get() = keyAttack

val Options.useKey: KeyBinding
    get() = keyUse

fun MultiPlayerGameMode.cancelBlockBreaking() {
    stopDestroyBlock()
}

fun MultiPlayerGameMode.interactBlock(
    player: net.minecraft.client.network.ClientPlayerEntity,
    hand: Hand,
    hitResult: BlockHitResult,
) = useItemOn(player, hand, hitResult)

fun MultiPlayerGameMode.attackBlock(pos: BlockPos, side: Direction): Boolean = startDestroyBlock(pos, side)

fun MultiPlayerGameMode.clickSlot(
    syncId: Int,
    slot: Int,
    button: Int,
    actionType: Any,
    player: PlayerEntity,
) {
    // 26.2 replaced the old click-slot client helper with structured container inputs.
    // Keep the hotbar-pick path compiling while the full inventory interaction port lands.
}

fun MultiPlayerGameMode.clickCreativeStack(stack: ItemStack, slot: Int) {
    handleCreativeModeItemAdd(stack, slot)
}

fun KeyBinding.wasPressed(): Boolean = consumeClick()

val KeyBinding.isPressed: Boolean
    get() = isDown

val Block.defaultState: BlockState
    get() = defaultBlockState()

val ClientWorld.time: Long
    get() = gameTime

val PlayerEntity.mainHandStack: ItemStack
    get() = getItemInHand(Hand.MAIN_HAND)

fun PlayerEntity.getStackInHand(hand: Hand): ItemStack = getItemInHand(hand)

fun PlayerEntity.swingHand(hand: Hand) {
    swing(hand)
}

var PlayerEntity.noClip: Boolean
    get() = noPhysics
    set(value) {
        noPhysics = value
    }

val PlayerEntity.isSpectator: Boolean
    get() = isSpectator()

val PlayerEntity.abilities: Abilities
    get() = getAbilities()

val PlayerEntity.currentScreenHandler: CompatScreenHandler
    get() = CompatScreenHandler(containerMenu.containerId)

data class CompatScreenHandler(val syncId: Int)

fun PlayerEntity.setNoGravity(noGravity: Boolean) {
    (this as Entity).setNoGravity(noGravity)
}

fun PlayerEntity.setOnGround(onGround: Boolean) {
    (this as Entity).setOnGround(onGround)
}

// horizontalCollision/verticalCollision: members exist directly on Entity in 26.1.
// (Extensions removed — they were shadowed by the inherited members.)

var Abilities.flySpeed: Float
    get() = flyingSpeed
    set(value) {
        setFlyingSpeed(value)
    }

val PlayerEntity.isInCreativeMode: Boolean
    get() = isCreative

val PlayerEntity.blockInteractionRange: Double
    get() = blockInteractionRange()

fun blockInteractionRangeOf(player: PlayerEntity): Double = player.blockInteractionRange()

val BlockHitResult.side: Direction
    get() = direction

val BlockHitResult.blockPos: BlockPos
    get() = BlockPos(getBlockPos())

val ItemPlacementContext.blockPos: BlockPos
    get() = BlockPos(clickedPos)

// canReplaceExisting(): exists as a member on ItemPlacementContext in 26.1.
// (Extension removed — it was shadowed by the member.)

fun BlockItem.getPlacementContext(context: ItemPlacementContext): ItemPlacementContext? {
    val result = updatePlacementContext(context) ?: return null
    return if (result is ItemPlacementContext) result
    else ItemPlacementContext(result.level, result.player!!, result.hand, result.itemInHand, net.minecraft.world.phys.BlockHitResult(result.clickLocation, result.clickedFace, result.clickedPos, result.isInside))
}

fun Block.getPlacementState(context: ItemPlacementContext): BlockState? {
    return getStateForPlacement(context)
}

fun BlockState.canPlaceAt(world: ClientWorld, pos: BlockPos): Boolean {
    return canSurvive(world, pos)
}

fun ClientWorld.raycast(context: RaycastContext): HitResult = clip(context)

fun ClientWorld.syncWorldEvent(player: PlayerEntity?, eventId: Int, pos: BlockPos, data: Int) {
    levelEvent(player, eventId, pos, data)
}

val HitResult.pos: Vec3d
    get() = location

fun Item.getMaxUseTime(stack: ItemStack, player: PlayerEntity): Int = getUseDuration(stack, player)

fun Item.getDefaultStack(): ItemStack = defaultInstance

fun PlayerInventory.getStack(slot: Int): ItemStack = getItem(slot)

fun PlayerInventory.setStack(slot: Int, stack: ItemStack) {
    setItem(slot, stack)
}

fun PlayerInventory.getEmptySlot(): Int = freeSlot

val PlayerInventory.mainStacks: List<ItemStack>
    get() = nonEquipmentItems

val BlockState.soundGroup
    get() = soundType

val Direction.vector: net.minecraft.core.Vec3i
    get() = unitVec3i

val Direction.opposite: Direction
    get() = getOpposite()

val Direction.offsetX: Int
    get() = stepX

val Direction.offsetY: Int
    get() = stepY

val Direction.offsetZ: Int
    get() = stepZ

fun BlockPos.offset(direction: Direction): BlockPos = relative(direction)

fun net.minecraft.core.BlockPos.toImmutable(): BlockPos = BlockPos(this)

fun Entity.getRotationVec(tickDelta: Float): Vec3d = getViewVector(tickDelta)

fun Entity.getCameraPosVec(tickDelta: Float): Vec3d = getEyePosition(tickDelta)

val Entity.pos: Vec3d
    get() = position()

val Entity.uuid: UUID
    get() = getUUID()

fun Vec3d.multiply(value: Double): Vec3d = scale(value)

fun Vec3d.squaredDistanceTo(other: Vec3d): Double = distanceToSqr(other)

fun AABB.contract(value: Double): AABB = contract(value, value, value)

fun VoxelShape.offset(x: Double, y: Double, z: Double): VoxelShape = move(x, y, z)

/** 26.2 moved the toast manager off Minecraft onto Gui. */
val MinecraftClient.toastManager
    get() = gui.toastManager()

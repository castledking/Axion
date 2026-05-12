package axion.client.tool

import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

val Minecraft.world get() = level

val Block.defaultState: BlockState get() = defaultBlockState()

val Direction.vector: Vec3i get() = unitVec3i
val Direction.offsetX: Int get() = stepX
val Direction.offsetY: Int get() = stepY
val Direction.offsetZ: Int get() = stepZ

val Entity.rotationVecClient: Vec3 get() = getViewVector(1.0f)
val Entity.uuid: java.util.UUID get() = getUUID()

fun Player.sendMessage(message: net.minecraft.network.chat.Component, actionBar: Boolean) {
    if (actionBar) (this as? net.minecraft.client.player.LocalPlayer)?.sendOverlayMessage(message) ?: sendSystemMessage(message)
    else sendSystemMessage(message)
}

val Player.isInCreativeMode: Boolean
    get() = gameMode() == net.minecraft.world.level.GameType.CREATIVE

fun LivingEntity.getStackInHand(hand: net.minecraft.world.InteractionHand) = getItemInHand(hand)

fun directionGetFacing(vec: Vec3): Direction = Direction.getApproximateNearest(vec)

fun floorMod(x: Int, y: Int): Int = net.minecraft.util.Mth.positiveModulo(x, y)

fun <T : Entity> net.minecraft.world.level.Level.getEntitiesByClass(
    clazz: Class<T>,
    box: AABB,
    predicate: (T) -> Boolean,
): List<T> {
    val result = mutableListOf<T>()
    getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(clazz), box, predicate, result)
    return result
}

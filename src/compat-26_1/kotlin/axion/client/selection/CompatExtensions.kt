package axion.client.selection

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

val Minecraft.world get() = level

fun Entity.getCameraPosVec(tickDelta: Float): Vec3 = getEyePosition(tickDelta)
fun Entity.getRotationVec(tickDelta: Float): Vec3 = getViewVector(tickDelta)
fun Entity.squaredDistanceTo(x: Double, y: Double, z: Double): Double = distanceToSqr(x, y, z)

fun net.minecraft.world.level.Level.raycast(context: ClipContext): BlockHitResult = clip(context)

val BlockHitResult.pos: Vec3 get() = location
val BlockHitResult.side: net.minecraft.core.Direction get() = direction
val net.minecraft.world.phys.HitResult.type get() = getType()

fun AABB.expand(x: Double, y: Double, z: Double): AABB = inflate(x, y, z)

fun net.minecraft.core.BlockPos.toImmutable(): net.minecraft.core.BlockPos = immutable()

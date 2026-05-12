package net.minecraft.util.math

import net.minecraft.core.Vec3i

typealias Box = net.minecraft.world.phys.AABB
typealias Direction = net.minecraft.core.Direction
typealias MathHelper = net.minecraft.util.Mth
typealias Vec3d = net.minecraft.world.phys.Vec3
typealias Axis = net.minecraft.core.Direction.Axis
typealias Vec3i = net.minecraft.core.Vec3i
typealias BlockPos = net.minecraft.core.BlockPos

// 26.1.x: Direction.unitVec3i provides the direction vector
val Direction.vector: Vec3i get() = this.unitVec3i

// 26.1.x: BlockPos.add() was renamed to offset()
fun BlockPos.add(x: Int, y: Int, z: Int): BlockPos = this.offset(x, y, z)
fun BlockPos.add(vec: Vec3i): BlockPos = this.offset(vec)

// 26.1.x: Vec3i.add() was renamed to offset() for BlockPos, but Vec3i doesn't have offset()
fun Vec3i.add(x: Int, y: Int, z: Int): Vec3i = Vec3i(this.x + x, this.y + y, this.z + z)
fun Vec3i.add(vec: Vec3i): Vec3i = Vec3i(this.x + vec.x, this.y + vec.y, this.z + vec.z)

// 26.1.x: BlockPos.toImmutable() is not needed since BlockPos is immutable in 26.1.x
fun BlockPos.toImmutable(): BlockPos = this

// 26.1.x: BlockPos.iterate() and ofFloored() companion-style methods
// Use top-level functions since typealias doesn't have Companion
fun blockPosIterate(min: BlockPos, max: BlockPos): Iterable<BlockPos> =
    net.minecraft.core.BlockPos.betweenClosed(min, max)

fun blockPosOfFloored(pos: Vec3d): BlockPos =
    BlockPos(
        kotlin.math.floor(pos.x).toInt(),
        kotlin.math.floor(pos.y).toInt(),
        kotlin.math.floor(pos.z).toInt()
    )

// 26.1.x: BlockPos.Mutable is now MutableBlockPos
typealias MutableBlockPos = net.minecraft.core.BlockPos.MutableBlockPos

// 26.1.x: BlockPos.unpackLongX/Y/Z are static methods
fun unpackLongX(packed: Long): Int = net.minecraft.core.BlockPos.getX(packed)
fun unpackLongY(packed: Long): Int = net.minecraft.core.BlockPos.getY(packed)
fun unpackLongZ(packed: Long): Int = net.minecraft.core.BlockPos.getZ(packed)

// 26.1.x: BlockPos.fromLong doesn't exist
fun blockPosFromLong(packed: Long): BlockPos = BlockPos(unpackLongX(packed), unpackLongY(packed), unpackLongZ(packed))



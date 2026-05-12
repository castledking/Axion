package axion.client.compat

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i

// Extension functions for BlockPos and Vec3i that are needed in the client module
// These are copied from compat-26_1 since that source set isn't included in client compilation

fun BlockPos.toImmutable(): BlockPos = this

fun BlockPos.add(x: Int, y: Int, z: Int): BlockPos = this.offset(x, y, z)
fun BlockPos.add(vec: Vec3i): BlockPos = this.offset(vec)

fun Vec3i.add(x: Int, y: Int, z: Int): Vec3i = Vec3i(this.x + x, this.y + y, this.z + z)
fun Vec3i.add(vec: Vec3i): Vec3i = Vec3i(this.x + vec.x, this.y + vec.y, this.z + vec.z)

// 26.1.x: BlockPos.Mutable is now MutableBlockPos
typealias MutableBlockPos = net.minecraft.core.BlockPos.MutableBlockPos

// 26.1.x: BlockPos.unpackLongX/Y/Z are static methods
fun unpackLongX(packed: Long): Int = BlockPos.getX(packed)
fun unpackLongY(packed: Long): Int = BlockPos.getY(packed)
fun unpackLongZ(packed: Long): Int = BlockPos.getZ(packed)

// 26.1.x: BlockPos.ORIGIN doesn't exist, add it as a constant
val ORIGIN: BlockPos = BlockPos(0, 0, 0)


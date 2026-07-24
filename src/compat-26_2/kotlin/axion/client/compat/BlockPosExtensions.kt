package axion.client.compat

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3

// BlockPos.betweenClosed reuses a MutableBlockPos while iterating. Always copy it
// before a position escapes the loop (for example into a move operation's write plan).
fun BlockPos.toImmutable(): BlockPos = BlockPos(x, y, z)

fun BlockPos.add(x: Int, y: Int, z: Int): BlockPos = this.offset(x, y, z)
fun BlockPos.add(vec: Vec3i): BlockPos = this.offset(vec)

fun Vec3i.add(x: Int, y: Int, z: Int): Vec3i = Vec3i(this.x + x, this.y + y, this.z + z)
fun Vec3i.add(vec: Vec3i): Vec3i = Vec3i(this.x + vec.x, this.y + vec.y, this.z + vec.z)

fun blockPosIterate(min: BlockPos, max: BlockPos): Iterable<BlockPos> =
    BlockPos.betweenClosed(min, max)

fun blockPosOfFloored(pos: Vec3): BlockPos =
    BlockPos(
        kotlin.math.floor(pos.x).toInt(),
        kotlin.math.floor(pos.y).toInt(),
        kotlin.math.floor(pos.z).toInt(),
    )

typealias MutableBlockPos = net.minecraft.core.BlockPos.MutableBlockPos

fun unpackLongX(packed: Long): Int = BlockPos.getX(packed)
fun unpackLongY(packed: Long): Int = BlockPos.getY(packed)
fun unpackLongZ(packed: Long): Int = BlockPos.getZ(packed)

fun blockPosFromLong(packed: Long): BlockPos = BlockPos(unpackLongX(packed), unpackLongY(packed), unpackLongZ(packed))

val ORIGIN: BlockPos = BlockPos(0, 0, 0)

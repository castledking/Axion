package axion.client.compat

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

// Required for cross-version import compatibility. Call sites resolve to the member
// when one exists (1.21.x); on 26.1 only the extension exists.
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun BlockPos.toImmutable(): BlockPos = BlockPos(x, y, z)

fun BlockPos.add(x: Int, y: Int, z: Int): BlockPos = BlockPos(this.x + x, this.y + y, this.z + z)
fun BlockPos.add(vec: Vec3i): BlockPos = add(vec.x, vec.y, vec.z)

fun Vec3i.add(x: Int, y: Int, z: Int): Vec3i = Vec3i(this.x + x, this.y + y, this.z + z)
fun Vec3i.add(vec: Vec3i): Vec3i = Vec3i(this.x + vec.x, this.y + vec.y, this.z + vec.z)

fun blockPosIterate(min: BlockPos, max: BlockPos): Iterable<BlockPos> =
    BlockPos.iterate(min, max)

fun blockPosOfFloored(pos: Vec3d): BlockPos =
    BlockPos.ofFloored(pos)

typealias MutableBlockPos = BlockPos.Mutable

fun unpackLongX(packed: Long): Int = BlockPos.unpackLongX(packed)
fun unpackLongY(packed: Long): Int = BlockPos.unpackLongY(packed)
fun unpackLongZ(packed: Long): Int = BlockPos.unpackLongZ(packed)

fun blockPosFromLong(packed: Long): BlockPos = BlockPos.fromLong(packed)

val ORIGIN: BlockPos = BlockPos.ORIGIN

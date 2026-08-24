package axion.common.compat

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i

fun BlockPos.offset(x: Int, y: Int, z: Int): BlockPos =
    BlockPos(this.x + x, this.y + y, this.z + z)

fun BlockPos.offset(delta: Vec3i): BlockPos =
    offset(delta.x, delta.y, delta.z)

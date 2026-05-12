package axion.client.hotbar

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

// Top-level functions for easy importing
fun blockPosIterate(min: BlockPos, max: BlockPos): Iterable<BlockPos> =
    BlockPos.betweenClosed(min, max)

fun blockPosOfFloored(pos: Vec3): BlockPos =
    BlockPos(
        kotlin.math.floor(pos.x).toInt(),
        kotlin.math.floor(pos.y).toInt(),
        kotlin.math.floor(pos.z).toInt()
    )

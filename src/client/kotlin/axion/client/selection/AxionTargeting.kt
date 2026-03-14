package axion.client.selection

import axion.common.model.RegionFace
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

sealed interface AxionTarget {
    data class BlockTarget(
        val blockPos: BlockPos,
        val hitPos: Vec3d,
        val squaredDistance: Double,
    ) : AxionTarget

    data class FaceTarget(
        val blockPos: BlockPos,
        val face: RegionFace,
        val hitPos: Vec3d,
        val squaredDistance: Double,
    ) : AxionTarget

    data object MissTarget : AxionTarget
}

object AxionTargeting {
    const val DEFAULT_REACH: Double = 256.0

    fun fromBlockHit(origin: Vec3d, hit: BlockHitResult): AxionTarget.FaceTarget {
        return AxionTarget.FaceTarget(
            blockPos = hit.blockPos.toImmutable(),
            face = hit.side.toRegionFace(),
            hitPos = hit.pos,
            squaredDistance = origin.squaredDistanceTo(hit.pos),
        )
    }
}

fun AxionTarget.blockPosOrNull(): BlockPos? = when (this) {
    is AxionTarget.BlockTarget -> blockPos
    is AxionTarget.FaceTarget -> blockPos
    AxionTarget.MissTarget -> null
}

fun AxionTarget.hitPosOrNull(): Vec3d? = when (this) {
    is AxionTarget.BlockTarget -> hitPos
    is AxionTarget.FaceTarget -> hitPos
    AxionTarget.MissTarget -> null
}

fun AxionTarget.asBlockTarget(): AxionTarget.BlockTarget? = when (this) {
    is AxionTarget.BlockTarget -> this
    is AxionTarget.FaceTarget -> AxionTarget.BlockTarget(blockPos, hitPos, squaredDistance)
    AxionTarget.MissTarget -> null
}

fun Direction.toRegionFace(): RegionFace = when (this) {
    Direction.DOWN -> RegionFace.DOWN
    Direction.UP -> RegionFace.UP
    Direction.NORTH -> RegionFace.NORTH
    Direction.SOUTH -> RegionFace.SOUTH
    Direction.WEST -> RegionFace.WEST
    Direction.EAST -> RegionFace.EAST
}

fun RegionFace.toDirection(): Direction = when (this) {
    RegionFace.DOWN -> Direction.DOWN
    RegionFace.UP -> Direction.UP
    RegionFace.NORTH -> Direction.NORTH
    RegionFace.SOUTH -> Direction.SOUTH
    RegionFace.WEST -> Direction.WEST
    RegionFace.EAST -> Direction.EAST
}

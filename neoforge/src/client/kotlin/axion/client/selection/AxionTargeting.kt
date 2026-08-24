package axion.client.current

import axion.common.model.RegionFace
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

sealed interface AxionTarget {
    data class BlockTarget(
        val blockPos: BlockPos,
        val hitPos: Vec3,
        val squaredDistance: Double,
    ) : AxionTarget

    data class FaceTarget(
        val blockPos: BlockPos,
        val face: RegionFace,
        val hitPos: Vec3,
        val squaredDistance: Double,
    ) : AxionTarget

    data object MissTarget : AxionTarget
}

object AxionTargeting {
    const val DEFAULT_REACH: Double = 256.0

    fun fromBlockHit(origin: Vec3, hit: BlockHitResult): AxionTarget.FaceTarget {
        val dx = hit.location.x - origin.x
        val dy = hit.location.y - origin.y
        val dz = hit.location.z - origin.z
        return AxionTarget.FaceTarget(
            blockPos = hit.blockPos.immutable(),
            face = hit.side.toRegionFace(),
            hitPos = hit.location,
            squaredDistance = dx * dx + dy * dy + dz * dz,
        )
    }
}

fun AxionTarget.blockPosOrNull(): BlockPos? = when (this) {
    is AxionTarget.BlockTarget -> blockPos
    is AxionTarget.FaceTarget -> blockPos
    AxionTarget.MissTarget -> null
}

fun AxionTarget.hitPosOrNull(): Vec3? = when (this) {
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

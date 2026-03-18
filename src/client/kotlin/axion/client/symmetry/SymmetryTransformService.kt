package axion.client.symmetry

import axion.common.model.SymmetryConfig
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

object SymmetryTransformService {
    fun transformedBlocks(config: SymmetryConfig, sourceBlock: BlockPos): List<BlockPos> {
        return activeTransforms(config)
            .map { transform -> transformBlock(sourceBlock, config.anchor.position, transform) }
            .distinct()
    }

    fun transformedBlocks(config: SymmetryConfig, sourceBlocks: Iterable<BlockPos>): List<BlockPos> {
        return sourceBlocks
            .flatMap { sourceBlock ->
                activeTransforms(config)
                    .map { transform -> transformBlock(sourceBlock, config.anchor.position, transform) }
            }
            .distinct()
    }

    fun activeTransforms(config: SymmetryConfig): List<SymmetryTransformSpec> {
        val transforms = linkedSetOf(SymmetryTransformSpec(rotationQuarterTurns = 0, mirrorY = false))
        val baseRotations = if (config.rotationalEnabled) 0..3 else 0..0
        baseRotations.forEach { turns ->
            transforms += SymmetryTransformSpec(rotationQuarterTurns = turns, mirrorY = false)
        }

        if (config.mirrorYEnabled) {
            baseRotations.forEach { turns ->
                transforms += SymmetryTransformSpec(rotationQuarterTurns = turns, mirrorY = true)
            }
        }

        return transforms.toList()
    }

    fun transformBlock(
        sourceBlock: BlockPos,
        anchor: Vec3d,
        transform: SymmetryTransformSpec,
    ): BlockPos {
        val transformedCenter = applyTransform(
            point = Vec3d.ofCenter(sourceBlock),
            anchor = anchor,
            rotationQuarterTurns = transform.rotationQuarterTurns,
            mirrorY = transform.mirrorY,
        )
        return BlockPos.ofFloored(transformedCenter)
    }

    fun transformVector(
        vector: Vec3i,
        transform: SymmetryTransformSpec,
    ): Vec3i {
        val rotated = when (Math.floorMod(transform.rotationQuarterTurns, 4)) {
            0 -> Vec3i(vector.x, vector.y, vector.z)
            1 -> Vec3i(-vector.z, vector.y, vector.x)
            2 -> Vec3i(-vector.x, vector.y, -vector.z)
            else -> Vec3i(vector.z, vector.y, -vector.x)
        }

        return if (transform.mirrorY) {
            Vec3i(rotated.x, -rotated.y, rotated.z)
        } else {
            rotated
        }
    }

    fun transformDirection(
        direction: Direction,
        transform: SymmetryTransformSpec,
    ): Direction {
        val transformed = transformVector(direction.vector, transform)
        return Direction.entries.first { candidate ->
            candidate.offsetX == transformed.x &&
                candidate.offsetY == transformed.y &&
                candidate.offsetZ == transformed.z
        }
    }

    private fun applyTransform(point: Vec3d, anchor: Vec3d, rotationQuarterTurns: Int, mirrorY: Boolean): Vec3d {
        val relativeX = point.x - anchor.x
        val relativeY = point.y - anchor.y
        val relativeZ = point.z - anchor.z

        val rotated = when (Math.floorMod(rotationQuarterTurns, 4)) {
            0 -> Vec3d(relativeX, relativeY, relativeZ)
            1 -> Vec3d(-relativeZ, relativeY, relativeX)
            2 -> Vec3d(-relativeX, relativeY, -relativeZ)
            else -> Vec3d(relativeZ, relativeY, -relativeX)
        }

        val mirroredY = if (mirrorY) -rotated.y else rotated.y
        return anchor.add(rotated.x, mirroredY, rotated.z)
    }
}

data class SymmetryTransformSpec(
    val rotationQuarterTurns: Int,
    val mirrorY: Boolean,
)

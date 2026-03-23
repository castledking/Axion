package axion.client.symmetry

import axion.common.model.SymmetryConfig
import axion.common.model.SymmetryMirrorAxis
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.math.roundToInt

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
        val transforms = linkedSetOf(SymmetryTransformSpec(rotationQuarterTurns = 0))
        val baseRotations = if (config.rotationalEnabled) 0..3 else 0..0
        baseRotations.forEach { turns ->
            transforms += SymmetryTransformSpec(rotationQuarterTurns = turns)
        }

        if (config.mirrorEnabled) {
            baseRotations.forEach { turns ->
                transforms += SymmetryTransformSpec(
                    rotationQuarterTurns = turns,
                    mirrorAxis = config.mirrorAxis,
                )
            }
        }

        return transforms.toList()
    }

    fun transformBlock(
        sourceBlock: BlockPos,
        anchor: Vec3d,
        transform: SymmetryTransformSpec,
    ): BlockPos {
        val rawAnchorX2 = (anchor.x * 2.0).roundToInt()
        val anchorY2 = (anchor.y * 2.0).roundToInt()
        val rawAnchorZ2 = (anchor.z * 2.0).roundToInt()
        val (anchorX2, anchorZ2) = effectiveHorizontalAnchor(rawAnchorX2, rawAnchorZ2, transform)

        val centerX2 = sourceBlock.x * 2 + 1
        val centerY2 = sourceBlock.y * 2 + 1
        val centerZ2 = sourceBlock.z * 2 + 1

        val relative = rotate(
            x = centerX2 - anchorX2,
            y = centerY2 - anchorY2,
            z = centerZ2 - anchorZ2,
            quarterTurns = transform.rotationQuarterTurns,
        )
        val mirrored = mirror(relative, transform.mirrorAxis)

        val transformedCenterX2 = anchorX2 + mirrored.x
        val transformedCenterY2 = anchorY2 + mirrored.y
        val transformedCenterZ2 = anchorZ2 + mirrored.z

        return BlockPos(
            Math.floorDiv(transformedCenterX2 - 1, 2),
            Math.floorDiv(transformedCenterY2 - 1, 2),
            Math.floorDiv(transformedCenterZ2 - 1, 2),
        )
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

        return when (transform.mirrorAxis) {
            null -> rotated
            SymmetryMirrorAxis.X -> Vec3i(-rotated.x, rotated.y, rotated.z)
            SymmetryMirrorAxis.Z -> Vec3i(rotated.x, rotated.y, -rotated.z)
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

    private fun rotate(x: Int, y: Int, z: Int, quarterTurns: Int): Vec3i {
        return when (Math.floorMod(quarterTurns, 4)) {
            0 -> Vec3i(x, y, z)
            1 -> Vec3i(-z, y, x)
            2 -> Vec3i(-x, y, -z)
            else -> Vec3i(z, y, -x)
        }
    }

    private fun mirror(vector: Vec3i, axis: SymmetryMirrorAxis?): Vec3i {
        return when (axis) {
            null -> vector
            SymmetryMirrorAxis.X -> Vec3i(-vector.x, vector.y, vector.z)
            SymmetryMirrorAxis.Z -> Vec3i(vector.x, vector.y, -vector.z)
        }
    }

    private fun effectiveHorizontalAnchor(
        anchorX2: Int,
        anchorZ2: Int,
        transform: SymmetryTransformSpec,
    ): Pair<Int, Int> {
        if (Math.floorMod(transform.rotationQuarterTurns, 4) == 0) {
            return anchorX2 to anchorZ2
        }
        if ((anchorX2 and 1) == (anchorZ2 and 1)) {
            return anchorX2 to anchorZ2
        }

        val adjustedX2 = if ((anchorX2 and 1) == 0) anchorX2 + 1 else anchorX2
        val adjustedZ2 = if ((anchorZ2 and 1) == 0) anchorZ2 + 1 else anchorZ2
        return adjustedX2 to adjustedZ2
    }
}

data class SymmetryTransformSpec(
    val rotationQuarterTurns: Int,
    val mirrorAxis: SymmetryMirrorAxis? = null,
)

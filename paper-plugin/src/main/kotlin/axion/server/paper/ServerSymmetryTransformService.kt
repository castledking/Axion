package axion.server.paper

import axion.protocol.DoubleVector3
import axion.protocol.IntVector3
import axion.protocol.SymmetryConfigPayload
import axion.protocol.SymmetryMirrorAxisPayload
import kotlin.math.roundToInt

object ServerSymmetryTransformService {
    fun activeTransforms(config: SymmetryConfigPayload?): List<TransformSpec> {
        if (config == null || (!config.rotationalEnabled && !config.mirrorEnabled)) {
            return listOf(TransformSpec(0))
        }

        val transforms = linkedSetOf(TransformSpec(0))
        val rotations = if (config.rotationalEnabled) 0..3 else 0..0
        rotations.forEach { turns ->
            transforms += TransformSpec(turns)
        }
        if (config.mirrorEnabled) {
            rotations.forEach { turns ->
                transforms += TransformSpec(turns, config.mirrorAxis)
            }
        }
        return transforms.toList()
    }

    fun transformBlock(source: IntVector3, anchor: DoubleVector3, transform: TransformSpec): IntVector3 {
        val anchorX2 = (anchor.x * 2.0).roundToInt()
        val anchorY2 = (anchor.y * 2.0).roundToInt()
        val anchorZ2 = (anchor.z * 2.0).roundToInt()

        val centerX2 = source.x * 2 + 1
        val centerY2 = source.y * 2 + 1
        val centerZ2 = source.z * 2 + 1

        val rotated = rotate(
            x = centerX2 - anchorX2,
            y = centerY2 - anchorY2,
            z = centerZ2 - anchorZ2,
            quarterTurns = transform.rotationQuarterTurns,
        )
        val mirrored = mirror(rotated, transform.mirrorAxis)

        val transformedCenterX2 = anchorX2 + mirrored.first
        val transformedCenterY2 = anchorY2 + mirrored.second
        val transformedCenterZ2 = anchorZ2 + mirrored.third

        return IntVector3(
            Math.floorDiv(transformedCenterX2 - 1, 2),
            Math.floorDiv(transformedCenterY2 - 1, 2),
            Math.floorDiv(transformedCenterZ2 - 1, 2),
        )
    }

    private fun rotate(x: Int, y: Int, z: Int, quarterTurns: Int): Triple<Int, Int, Int> {
        return when (Math.floorMod(quarterTurns, 4)) {
            0 -> Triple(x, y, z)
            1 -> Triple(-z, y, x)
            2 -> Triple(-x, y, -z)
            else -> Triple(z, y, -x)
        }
    }

    private fun mirror(vector: Triple<Int, Int, Int>, axis: SymmetryMirrorAxisPayload?): Triple<Int, Int, Int> {
        return when (axis) {
            null -> vector
            SymmetryMirrorAxisPayload.X -> Triple(-vector.first, vector.second, vector.third)
            SymmetryMirrorAxisPayload.Z -> Triple(vector.first, vector.second, -vector.third)
        }
    }

    data class TransformSpec(
        val rotationQuarterTurns: Int,
        val mirrorAxis: SymmetryMirrorAxisPayload? = null,
    )
}

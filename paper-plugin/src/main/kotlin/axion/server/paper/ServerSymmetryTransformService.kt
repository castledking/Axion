package axion.server.paper

import axion.protocol.DoubleVector3
import axion.protocol.IntVector3
import axion.protocol.SymmetryConfigPayload

object ServerSymmetryTransformService {
    fun activeTransforms(config: SymmetryConfigPayload?): List<TransformSpec> {
        if (config == null || (!config.rotationalEnabled && !config.mirrorYEnabled)) {
            return listOf(TransformSpec(0, false))
        }

        val transforms = linkedSetOf(TransformSpec(0, false))
        val rotations = if (config.rotationalEnabled) 0..3 else 0..0
        rotations.forEach { turns ->
            transforms += TransformSpec(turns, false)
        }
        if (config.mirrorYEnabled) {
            rotations.forEach { turns ->
                transforms += TransformSpec(turns, true)
            }
        }
        return transforms.toList()
    }

    fun transformBlock(source: IntVector3, anchor: DoubleVector3, transform: TransformSpec): IntVector3 {
        val centerX = source.x + 0.5
        val centerY = source.y + 0.5
        val centerZ = source.z + 0.5
        val relativeX = centerX - anchor.x
        val relativeY = centerY - anchor.y
        val relativeZ = centerZ - anchor.z
        val rotated = when (Math.floorMod(transform.rotationQuarterTurns, 4)) {
            0 -> Triple(relativeX, relativeY, relativeZ)
            1 -> Triple(-relativeZ, relativeY, relativeX)
            2 -> Triple(-relativeX, relativeY, -relativeZ)
            else -> Triple(relativeZ, relativeY, -relativeX)
        }
        val mirroredY = if (transform.mirrorY) -rotated.second else rotated.second
        return IntVector3(
            kotlin.math.floor(anchor.x + rotated.first).toInt(),
            kotlin.math.floor(anchor.y + mirroredY).toInt(),
            kotlin.math.floor(anchor.z + rotated.third).toInt(),
        )
    }

    data class TransformSpec(
        val rotationQuarterTurns: Int,
        val mirrorY: Boolean,
    )
}

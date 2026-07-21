package axion.protocol

import kotlin.math.floor

enum class EntitySelectionMode {
    FULL_REGION,
    SPARSE_OFFSETS,
}

/**
 * Block cells which are allowed to select entities from a source region.
 *
 * Offsets are relative to the normalized source minimum. Full cuboids stay
 * compact on the wire while magic selections retain their exact sparse cells.
 */
class EntitySelectionMask private constructor(
    val mode: EntitySelectionMode,
    val offsets: List<IntVector3>,
) {
    val isEmpty: Boolean
        get() = mode == EntitySelectionMode.SPARSE_OFFSETS && offsets.isEmpty()

    fun selectedBlockCount(sourceSize: IntVector3): Long = when (mode) {
        EntitySelectionMode.FULL_REGION -> if (sourceSize.hasPositiveComponents()) sourceSize.volume() else 0L
        EntitySelectionMode.SPARSE_OFFSETS -> offsets.size.toLong()
    }

    fun matcher(sourceMin: IntVector3, sourceMax: IntVector3): EntitySelectionMatcher =
        EntitySelectionMatcher(sourceMin, sourceMax, this)

    fun selectedOffsets(sourceSize: IntVector3): Sequence<IntVector3> {
        require(sourceSize.hasPositiveComponents()) { "Entity selection source size must be positive" }
        return when (mode) {
            EntitySelectionMode.FULL_REGION -> sequence {
                for (x in 0 until sourceSize.x) {
                    for (y in 0 until sourceSize.y) {
                        for (z in 0 until sourceSize.z) {
                            yield(IntVector3(x, y, z))
                        }
                    }
                }
            }

            EntitySelectionMode.SPARSE_OFFSETS -> offsets.asSequence()
        }
    }

    fun repeated(
        sourceSize: IntVector3,
        step: IntVector3,
        repeatCount: Int,
    ): RepeatedEntitySelectionMask {
        require(sourceSize.hasPositiveComponents()) { "Entity selection source size must be positive" }
        require(repeatCount >= 0) { "Entity selection repeat count must not be negative" }
        require(isValidFor(sourceSize)) { "Entity selection offset is outside its source region" }

        val terminal = step.timesChecked(repeatCount)
        val relativeMin = IntVector3(
            minOf(0, terminal.x),
            minOf(0, terminal.y),
            minOf(0, terminal.z),
        )
        val relativeMax = IntVector3(
            maxOf(sourceSize.x - 1, terminal.x.plusChecked(sourceSize.x - 1)),
            maxOf(sourceSize.y - 1, terminal.y.plusChecked(sourceSize.y - 1)),
            maxOf(sourceSize.z - 1, terminal.z.plusChecked(sourceSize.z - 1)),
        )
        val repeatedSize = IntVector3(
            relativeMax.x.minusChecked(relativeMin.x).plusChecked(1),
            relativeMax.y.minusChecked(relativeMin.y).plusChecked(1),
            relativeMax.z.minusChecked(relativeMin.z).plusChecked(1),
        )
        val normalizedOffsets = buildList {
            for (index in 0..repeatCount) {
                val repetitionOffset = step.timesChecked(index)
                selectedOffsets(sourceSize).forEach { sourceOffset ->
                    add(
                        IntVector3(
                            sourceOffset.x.plusChecked(repetitionOffset.x).minusChecked(relativeMin.x),
                            sourceOffset.y.plusChecked(repetitionOffset.y).minusChecked(relativeMin.y),
                            sourceOffset.z.plusChecked(repetitionOffset.z).minusChecked(relativeMin.z),
                        ),
                    )
                }
            }
        }
        return RepeatedEntitySelectionMask(
            relativeOrigin = relativeMin,
            size = repeatedSize,
            mask = fromSelectedOffsets(repeatedSize, normalizedOffsets),
        )
    }

    fun isValidFor(sourceSize: IntVector3): Boolean =
        sourceSize.hasPositiveComponents() &&
            (mode == EntitySelectionMode.FULL_REGION || offsets.all { it.isInside(sourceSize) })

    override fun equals(other: Any?): Boolean =
        this === other || other is EntitySelectionMask && mode == other.mode && offsets == other.offsets

    override fun hashCode(): Int = 31 * mode.hashCode() + offsets.hashCode()

    override fun toString(): String = "EntitySelectionMask(mode=$mode, offsets=$offsets)"

    companion object {
        const val MAX_SPARSE_OFFSETS: Int = 2_000_000

        private val FULL = EntitySelectionMask(EntitySelectionMode.FULL_REGION, emptyList())

        fun fullRegion(): EntitySelectionMask = FULL

        fun sparseOffsets(offsets: Iterable<IntVector3>): EntitySelectionMask {
            val unique = offsets.distinct()
            require(unique.size <= MAX_SPARSE_OFFSETS) { "Entity selection has too many sparse offsets" }
            return EntitySelectionMask(
                mode = EntitySelectionMode.SPARSE_OFFSETS,
                offsets = unique,
            )
        }

        fun fromSelectedOffsets(
            sourceSize: IntVector3,
            selectedOffsets: Iterable<IntVector3>,
        ): EntitySelectionMask {
            require(sourceSize.hasPositiveComponents()) { "Entity selection source size must be positive" }
            val unique = selectedOffsets.distinct()
            require(unique.all { it.isInside(sourceSize) }) { "Entity selection offset is outside its source region" }
            return if (unique.size.toLong() == sourceSize.volume()) {
                fullRegion()
            } else {
                sparseOffsets(unique)
            }
        }
    }
}

data class RepeatedEntitySelectionMask(
    val relativeOrigin: IntVector3,
    val size: IntVector3,
    val mask: EntitySelectionMask,
)

object EntitySelectionGeometry {
    fun sourceSize(sourceMin: IntVector3, sourceMax: IntVector3): IntVector3 {
        val min = minVector(sourceMin, sourceMax)
        val max = maxVector(sourceMin, sourceMax)
        return IntVector3(
            componentSize(min.x, max.x),
            componentSize(min.y, max.y),
            componentSize(min.z, max.z),
        )
    }

    fun sourcePositions(
        sourceMin: IntVector3,
        sourceMax: IntVector3,
        mask: EntitySelectionMask,
    ): Sequence<IntVector3> {
        val min = minVector(sourceMin, sourceMax)
        val size = sourceSize(sourceMin, sourceMax)
        require(mask.isValidFor(size)) { "Entity selection offset is outside its source region" }
        return mask.selectedOffsets(size).map { offset ->
            IntVector3(
                min.x.plusChecked(offset.x),
                min.y.plusChecked(offset.y),
                min.z.plusChecked(offset.z),
            )
        }
    }

    fun destinationPositions(
        sourceMin: IntVector3,
        sourceMax: IntVector3,
        destinationOrigin: IntVector3,
        rotationQuarterTurns: Int,
        mirrorAxis: PlacementMirrorAxisPayload,
        mask: EntitySelectionMask,
    ): Sequence<IntVector3> {
        val size = sourceSize(sourceMin, sourceMax)
        require(mask.isValidFor(size)) { "Entity selection offset is outside its source region" }
        return mask.selectedOffsets(size).map { offset ->
            val transformed = transformOffset(size, offset, rotationQuarterTurns, mirrorAxis)
            IntVector3(
                destinationOrigin.x.plusChecked(transformed.x),
                destinationOrigin.y.plusChecked(transformed.y),
                destinationOrigin.z.plusChecked(transformed.z),
            )
        }
    }

    fun transformOffset(
        sourceSize: IntVector3,
        offset: IntVector3,
        rotationQuarterTurns: Int,
        mirrorAxis: PlacementMirrorAxisPayload,
    ): IntVector3 {
        require(sourceSize.hasPositiveComponents()) { "Entity selection source size must be positive" }
        require(offset.isInside(sourceSize)) { "Entity selection offset is outside its source region" }
        val mirrored = when (mirrorAxis) {
            PlacementMirrorAxisPayload.NONE -> offset
            PlacementMirrorAxisPayload.X -> IntVector3(sourceSize.x - 1 - offset.x, offset.y, offset.z)
            PlacementMirrorAxisPayload.Y -> IntVector3(offset.x, sourceSize.y - 1 - offset.y, offset.z)
            PlacementMirrorAxisPayload.Z -> IntVector3(offset.x, offset.y, sourceSize.z - 1 - offset.z)
        }
        return when (Math.floorMod(rotationQuarterTurns, 4)) {
            0 -> mirrored
            1 -> IntVector3(sourceSize.z - 1 - mirrored.z, mirrored.y, mirrored.x)
            2 -> IntVector3(sourceSize.x - 1 - mirrored.x, mirrored.y, sourceSize.z - 1 - mirrored.z)
            else -> IntVector3(mirrored.z, mirrored.y, sourceSize.x - 1 - mirrored.x)
        }
    }

    private fun componentSize(min: Int, max: Int): Int {
        val size = max.toLong() - min.toLong() + 1L
        require(size in 1..Int.MAX_VALUE.toLong()) { "Entity selection source dimension is too large" }
        return size.toInt()
    }

    private fun minVector(a: IntVector3, b: IntVector3): IntVector3 = IntVector3(
        minOf(a.x, b.x),
        minOf(a.y, b.y),
        minOf(a.z, b.z),
    )

    private fun maxVector(a: IntVector3, b: IntVector3): IntVector3 = IntVector3(
        maxOf(a.x, b.x),
        maxOf(a.y, b.y),
        maxOf(a.z, b.z),
    )
}

class EntitySelectionMatcher internal constructor(
    sourceMinInput: IntVector3,
    sourceMaxInput: IntVector3,
    mask: EntitySelectionMask,
) {
    private val sourceMin = IntVector3(
        minOf(sourceMinInput.x, sourceMaxInput.x),
        minOf(sourceMinInput.y, sourceMaxInput.y),
        minOf(sourceMinInput.z, sourceMaxInput.z),
    )
    private val sourceMax = IntVector3(
        maxOf(sourceMinInput.x, sourceMaxInput.x),
        maxOf(sourceMinInput.y, sourceMaxInput.y),
        maxOf(sourceMinInput.z, sourceMaxInput.z),
    )
    private val fullRegion = mask.mode == EntitySelectionMode.FULL_REGION
    private val selectedYByColumn: Map<Long, IntArray> = if (fullRegion) {
        emptyMap()
    } else {
        mask.offsets
            .groupBy { columnKey(sourceMin.x + it.x, sourceMin.z + it.z) }
            .mapValues { (_, offsets) -> offsets.map { sourceMin.y + it.y }.distinct().toIntArray() }
    }

    fun containsFeet(x: Double, y: Double, z: Double): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
            return false
        }
        val blockX = floor(x).toInt()
        val blockZ = floor(z).toInt()
        if (fullRegion) {
            return blockX in sourceMin.x..sourceMax.x &&
                blockZ in sourceMin.z..sourceMax.z &&
                y + POSITION_EPSILON >= sourceMin.y &&
                y <= sourceMax.y + ON_TOP_HEIGHT + POSITION_EPSILON
        }
        val selectedY = selectedYByColumn[columnKey(blockX, blockZ)] ?: return false
        return selectedY.any { blockY ->
            y + POSITION_EPSILON >= blockY && y <= blockY + ON_TOP_HEIGHT + POSITION_EPSILON
        }
    }

    private companion object {
        private const val ON_TOP_HEIGHT: Double = 1.25
        private const val POSITION_EPSILON: Double = 1.0e-7

        private fun columnKey(x: Int, z: Int): Long =
            (x.toLong() shl 32) xor (z.toLong() and 0xffff_ffffL)
    }
}

private fun IntVector3.hasPositiveComponents(): Boolean = x > 0 && y > 0 && z > 0

private fun IntVector3.isInside(size: IntVector3): Boolean =
    x in 0 until size.x && y in 0 until size.y && z in 0 until size.z

private fun IntVector3.volume(): Long {
    if (!hasPositiveComponents()) return 0L
    val xy = if (x.toLong() > Long.MAX_VALUE / y.toLong()) Long.MAX_VALUE else x.toLong() * y.toLong()
    return if (xy > Long.MAX_VALUE / z.toLong()) Long.MAX_VALUE else xy * z.toLong()
}

private fun IntVector3.timesChecked(multiplier: Int): IntVector3 = IntVector3(
    Math.multiplyExact(x, multiplier),
    Math.multiplyExact(y, multiplier),
    Math.multiplyExact(z, multiplier),
)

private fun Int.plusChecked(other: Int): Int = Math.addExact(this, other)

private fun Int.minusChecked(other: Int): Int = Math.subtractExact(this, other)

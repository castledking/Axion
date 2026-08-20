package axion.client.mode

import net.minecraft.util.math.Direction

object ReplacePlacementPolicy {
    private const val REPLACE_COOLDOWN_TICKS: Int = 2

    enum class SlabHalf {
        TOP,
        BOTTOM,
    }

    fun cooldownTicks(fastPlaceEnabled: Boolean, replaceModeEnabled: Boolean): Int = when {
        fastPlaceEnabled -> 0
        replaceModeEnabled -> REPLACE_COOLDOWN_TICKS
        else -> 4
    }

    fun maxSamples(
        fastPlaceEnabled: Boolean,
        replaceModeEnabled: Boolean,
        configuredMaximum: Int,
    ): Int {
        return if (replaceModeEnabled && !fastPlaceEnabled) 1 else configuredMaximum
    }

    /** Mirrors vanilla's fresh-slab half choice without consulting the slab being replaced. */
    fun singleSlabHalf(side: Direction, hitYWithinBlock: Double): SlabHalf {
        return if (side != Direction.DOWN && (side == Direction.UP || hitYWithinBlock <= 0.5)) {
            SlabHalf.BOTTOM
        } else {
            SlabHalf.TOP
        }
    }
}

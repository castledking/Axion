package axion.client.mode

import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplacePlacementPolicyTest {
    @Test
    fun `replace-only placement is limited to ten updates per second`() {
        assertEquals(
            2,
            ReplacePlacementPolicy.cooldownTicks(
                fastPlaceEnabled = false,
                replaceModeEnabled = true,
            ),
        )
    }

    @Test
    fun `explicit fast place retains its every-tick cadence`() {
        assertEquals(
            0,
            ReplacePlacementPolicy.cooldownTicks(
                fastPlaceEnabled = true,
                replaceModeEnabled = true,
            ),
        )
    }

    @Test
    fun `replace-only handles one targeted block while explicit fast place keeps sampling`() {
        assertEquals(
            1,
            ReplacePlacementPolicy.maxSamples(
                fastPlaceEnabled = false,
                replaceModeEnabled = true,
                configuredMaximum = 50,
            ),
        )
        assertEquals(
            50,
            ReplacePlacementPolicy.maxSamples(
                fastPlaceEnabled = true,
                replaceModeEnabled = true,
                configuredMaximum = 50,
            ),
        )
    }

    @Test
    fun `replacement slab never inherits the existing double state`() {
        assertEquals(
            ReplacePlacementPolicy.SlabHalf.BOTTOM,
            ReplacePlacementPolicy.singleSlabHalf(Direction.UP, hitYWithinBlock = 1.0),
        )
        assertEquals(
            ReplacePlacementPolicy.SlabHalf.TOP,
            ReplacePlacementPolicy.singleSlabHalf(Direction.DOWN, hitYWithinBlock = 0.0),
        )
        assertEquals(
            ReplacePlacementPolicy.SlabHalf.TOP,
            ReplacePlacementPolicy.singleSlabHalf(Direction.NORTH, hitYWithinBlock = 0.75),
        )
    }
}

package axion.client.symmetry

import axion.protocol.AxionInteractionOrigin
import kotlin.test.Test
import kotlin.test.assertEquals

class SymmetryPlacementOriginPolicyTest {
    @Test
    fun `far symmetry placements inherit infinite reach protection`() {
        assertEquals(
            AxionInteractionOrigin.INFINITE_REACH,
            SymmetryPlacementOriginPolicy.forTarget(
                infiniteReachEnabled = true,
                beyondVanillaReach = true,
            ),
        )
    }

    @Test
    fun `near or ordinary symmetry placements keep normal routing`() {
        assertEquals(
            AxionInteractionOrigin.NONE,
            SymmetryPlacementOriginPolicy.forTarget(
                infiniteReachEnabled = true,
                beyondVanillaReach = false,
            ),
        )
        assertEquals(
            AxionInteractionOrigin.NONE,
            SymmetryPlacementOriginPolicy.forTarget(
                infiniteReachEnabled = false,
                beyondVanillaReach = true,
            ),
        )
    }
}

package axion.client.mode

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AngelPlacementPolicyTest {
    @Test
    fun `no mid-air target while the capability is off`() {
        assertFalse(
            AngelPlacementPolicy.offersMidAirTarget(
                angelPlacementEnabled = false,
                blockTargetPresent = false,
            ),
        )
    }

    @Test
    fun `a block under the crosshair keeps the click on the normal path`() {
        // This is what makes infinite reach "take over": its longer ray finds a
        // distant block, so blockTargetPresent is true and no ghost is offered.
        assertFalse(
            AngelPlacementPolicy.offersMidAirTarget(
                angelPlacementEnabled = true,
                blockTargetPresent = true,
            ),
        )
    }

    @Test
    fun `open sky still offers a mid-air target`() {
        assertTrue(
            AngelPlacementPolicy.offersMidAirTarget(
                angelPlacementEnabled = true,
                blockTargetPresent = false,
            ),
        )
    }

    @Test
    fun `the ghost sits inside vanilla reach`() {
        // Keeps the placement an ordinary short-range interaction, so it needs no
        // infinite-reach transport and no extra server-side reach allowance.
        assertTrue(AngelPlacementPolicy.ghostWithinVanillaReach(vanillaReach = 4.5))
        assertTrue(AngelPlacementPolicy.ghostWithinVanillaReach(vanillaReach = 6.0))
    }
}

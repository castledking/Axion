package axion.common.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StairMirrorPolicyTest {
    @Test
    fun `a stair vanilla left untouched still needs its corner flipped`() {
        // Vanilla rotates the facing 180 degrees whenever it handled the stair, so
        // an unchanged state means it took the "facing crosses the mirror" branch
        // and skipped the handedness swap.
        assertTrue(
            StairMirrorPolicy.needsHandednessFlip(
                isStairs = true,
                mirrorLeftStateUnchanged = true,
            ),
        )
    }

    @Test
    fun `a stair vanilla already mirrored is left alone`() {
        assertFalse(
            StairMirrorPolicy.needsHandednessFlip(
                isStairs = true,
                mirrorLeftStateUnchanged = false,
            ),
        )
    }

    @Test
    fun `blocks that are not stairs are never touched`() {
        // Plenty of blocks mirror to themselves — full cubes, pillars on the
        // untouched axis — and none of them carry corner handedness.
        assertFalse(
            StairMirrorPolicy.needsHandednessFlip(
                isStairs = false,
                mirrorLeftStateUnchanged = true,
            ),
        )
    }
}

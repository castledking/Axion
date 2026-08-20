package axion.client.symmetry

import axion.protocol.AxionInteractionOrigin
import kotlin.test.Test
import kotlin.test.assertEquals

class SymmetryBreakOriginPolicyTest {
    @Test
    fun `derived breaks inherit active infinite reach protection`() {
        assertEquals(
            AxionInteractionOrigin.INFINITE_REACH,
            SymmetryBreakOriginPolicy.forPrimaryBreak(infiniteReachEnabled = true),
        )
        assertEquals(
            AxionInteractionOrigin.NONE,
            SymmetryBreakOriginPolicy.forPrimaryBreak(infiniteReachEnabled = false),
        )
    }
}

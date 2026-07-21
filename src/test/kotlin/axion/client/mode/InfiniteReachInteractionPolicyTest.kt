package axion.client.mode

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InfiniteReachInteractionPolicyTest {
    @Test
    fun `infinite reach yields to a target already found by vanilla`() {
        assertTrue(
            InfiniteReachInteractionPolicy.shouldYieldToVanilla(
                infiniteReachEnabled = true,
                replaceModeEnabled = false,
                vanillaTargetPresent = true,
            ),
        )
    }

    @Test
    fun `remote building remains active beyond vanilla targeting`() {
        assertFalse(
            InfiniteReachInteractionPolicy.shouldYieldToVanilla(
                infiniteReachEnabled = true,
                replaceModeEnabled = false,
                vanillaTargetPresent = false,
            ),
        )
    }

    @Test
    fun `explicit replace mode continues to own its target`() {
        assertFalse(
            InfiniteReachInteractionPolicy.shouldYieldToVanilla(
                infiniteReachEnabled = true,
                replaceModeEnabled = true,
                vanillaTargetPresent = true,
            ),
        )
    }
}

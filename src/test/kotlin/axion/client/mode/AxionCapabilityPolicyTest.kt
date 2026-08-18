package axion.client.mode

import axion.common.model.GlobalModeState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AxionCapabilityPolicyTest {
    @Test
    fun `vanilla clicks stay with vanilla when nothing is armed`() {
        val state = GlobalModeState()

        assertFalse(AxionCapabilityPolicy.ownsPlacement(state))
        assertFalse(AxionCapabilityPolicy.ownsBreak(state))
        assertFalse(AxionCapabilityPolicy.suppressBlockUpdates(state))
        assertFalse(AxionCapabilityPolicy.ignoresEntityCollision(state))
    }

    @Test
    fun `force place and no updates each take over placement on their own`() {
        assertTrue(AxionCapabilityPolicy.ownsPlacement(GlobalModeState(forcePlaceEnabled = true)))
        assertTrue(AxionCapabilityPolicy.ownsPlacement(GlobalModeState(noUpdatesEnabled = true)))
        assertTrue(AxionCapabilityPolicy.ownsPlacement(GlobalModeState(replaceModeEnabled = true)))
        assertTrue(AxionCapabilityPolicy.ownsPlacement(GlobalModeState(infiniteReachEnabled = true)))
    }

    @Test
    fun `only no updates takes over an ordinary break`() {
        assertTrue(AxionCapabilityPolicy.ownsBreak(GlobalModeState(noUpdatesEnabled = true)))
        // Infinite reach and bulldozer already own their own break paths, and
        // force place has nothing to say about breaking.
        assertFalse(AxionCapabilityPolicy.ownsBreak(GlobalModeState(infiniteReachEnabled = true)))
        assertFalse(AxionCapabilityPolicy.ownsBreak(GlobalModeState(bulldozerEnabled = true)))
        assertFalse(AxionCapabilityPolicy.ownsBreak(GlobalModeState(forcePlaceEnabled = true)))
    }

    @Test
    fun `replace mode and infinite reach keep vanilla physics until no updates is armed`() {
        val combined = GlobalModeState(replaceModeEnabled = true, infiniteReachEnabled = true)

        assertFalse(AxionCapabilityPolicy.suppressBlockUpdates(combined))
        assertTrue(AxionCapabilityPolicy.suppressBlockUpdates(combined.copy(noUpdatesEnabled = true)))
    }

    @Test
    fun `only force place ignores entity collision`() {
        assertTrue(AxionCapabilityPolicy.ignoresEntityCollision(GlobalModeState(forcePlaceEnabled = true)))
        assertFalse(AxionCapabilityPolicy.ignoresEntityCollision(GlobalModeState(noClipEnabled = true)))
    }
}

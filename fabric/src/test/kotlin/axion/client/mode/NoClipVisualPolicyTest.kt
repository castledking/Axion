package axion.client.mode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoClipVisualPolicyTest {
    @Test
    fun activeNoClipKeepsTheRequestedThirdPersonDistance() {
        assertEquals(4.0f, NoClipVisualPolicy.cameraDistanceOverride(4.0f, noClipActive = true))
    }

    @Test
    fun inactiveNoClipLeavesVanillaCameraCollisionInControl() {
        assertNull(NoClipVisualPolicy.cameraDistanceOverride(4.0f, noClipActive = false))
    }

    @Test
    fun onlyActiveNoClipSuppressesTheInWallOverlay() {
        assertTrue(NoClipVisualPolicy.suppressInWallOverlay(noClipActive = true))
        assertFalse(NoClipVisualPolicy.suppressInWallOverlay(noClipActive = false))
    }

    @Test
    fun activeNoClipEscapesTheLowCrawlPose() {
        assertTrue(NoClipVisualPolicy.forceStandingPose(noClipActive = true))
        assertFalse(NoClipVisualPolicy.forceStandingPose(noClipActive = false))
    }
}

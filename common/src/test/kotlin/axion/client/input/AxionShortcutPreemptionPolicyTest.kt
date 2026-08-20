package axion.client.input

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AxionShortcutPreemptionPolicyTest {
    @Test
    fun `ctrl flip suppresses an overlapping offhand swap`() {
        assertTrue(
            AxionShortcutPreemptionPolicy.shouldSuppressOffhandSwap(
                controlDown = true,
                mirrorKeyDown = true,
                canFlipPreview = true,
                canToggleMirror = false,
            ),
        )
    }

    @Test
    fun `ctrl mirror suppresses an overlapping offhand swap`() {
        assertTrue(
            AxionShortcutPreemptionPolicy.shouldSuppressOffhandSwap(
                controlDown = true,
                mirrorKeyDown = true,
                canFlipPreview = false,
                canToggleMirror = true,
            ),
        )
    }

    @Test
    fun `ordinary offhand input remains vanilla`() {
        assertFalse(
            AxionShortcutPreemptionPolicy.shouldSuppressOffhandSwap(
                controlDown = false,
                mirrorKeyDown = true,
                canFlipPreview = true,
                canToggleMirror = true,
            ),
        )
        assertFalse(
            AxionShortcutPreemptionPolicy.shouldSuppressOffhandSwap(
                controlDown = true,
                mirrorKeyDown = true,
                canFlipPreview = false,
                canToggleMirror = false,
            ),
        )
    }
}

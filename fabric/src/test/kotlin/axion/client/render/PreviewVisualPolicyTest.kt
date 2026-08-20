package axion.client.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewVisualPolicyTest {
    @Test
    fun `block previews depth-test against terrain so they are occluded by foreground blocks`() {
        assertFalse(PreviewVisualPolicy.XRAY_BLOCK_PREVIEWS)
    }

    @Test
    fun `culled destination shell leaves approximately twenty percent of the scene visible`() {
        val alpha = PreviewVisualPolicy.alphaForTransmission(
            PreviewVisualPolicy.TARGET_SCENE_TRANSMISSION,
            PreviewVisualPolicy.VISIBLE_DESTINATION_SHELL_CROSSINGS,
        )

        assertEquals(204, alpha)
        assertEquals(alpha, PreviewVisualPolicy.DESTINATION_ALPHA)
        assertEquals(alpha, PreviewVisualPolicy.MOVE_DESTINATION_ALPHA)
        assertEquals(alpha, PreviewVisualPolicy.CULLED_DESTINATION_ALPHA)
        assertEquals(
            PreviewVisualPolicy.TARGET_SCENE_TRANSMISSION,
            PreviewVisualPolicy.compoundedTransmission(
                alpha,
                PreviewVisualPolicy.VISIBLE_DESTINATION_SHELL_CROSSINGS,
            ),
            absoluteTolerance = 0.002,
        )
    }

    @Test
    fun `destination ghosts drop texel alpha so their opacity stays the policy constant`() {
        assertTrue(PreviewVisualPolicy.ignoresTextureAlpha("ghost:placement-destination"))
        assertTrue(PreviewVisualPolicy.ignoresTextureAlpha("ghost:default"))
        assertEquals(
            PreviewVisualPolicy.TARGET_SCENE_TRANSMISSION,
            PreviewVisualPolicy.compoundedTransmission(
                PreviewVisualPolicy.DESTINATION_ALPHA,
                PreviewVisualPolicy.VISIBLE_DESTINATION_SHELL_CROSSINGS,
            ),
            absoluteTolerance = 0.002,
        )
    }

    @Test
    fun `move source glass keeps vanilla texel alpha`() {
        assertFalse(
            PreviewVisualPolicy.ignoresTextureAlpha(
                "ghost:${PreviewBlockIdentityPolicy.MOVE_SOURCE_SESSION_TAG}",
            ),
        )
    }

    @Test
    fun `move source glass uses full modulator alpha so the vanilla texture controls opacity`() {
        assertEquals(255, PreviewVisualPolicy.MOVE_SOURCE_ALPHA)
        assertEquals(
            PreviewVisualPolicy.MOVE_SOURCE_ALPHA,
            PreviewVisualPolicy.CULLED_MOVE_SOURCE_ALPHA,
        )
    }

    @Test
    fun `all destination shells match the 26_1_2 transparency`() {
        val expected = PreviewVisualPolicy.alphaForTransmission(
            PreviewVisualPolicy.TARGET_SCENE_TRANSMISSION,
            PreviewVisualPolicy.VISIBLE_DESTINATION_SHELL_CROSSINGS,
        )

        assertEquals(204, expected)
        assertEquals(expected, PreviewVisualPolicy.DESTINATION_ALPHA)
        assertEquals(expected, PreviewVisualPolicy.MOVE_DESTINATION_ALPHA)
        assertEquals(expected, PreviewVisualPolicy.SPARSE_DESTINATION_ALPHA)
        assertEquals(expected, PreviewVisualPolicy.CULLED_DESTINATION_ALPHA)
        assertEquals(expected, PreviewVisualPolicy.CULLED_SPARSE_DESTINATION_ALPHA)
    }

    @Test
    fun `placement pulse remains visible at its color crossover`() {
        val min = PreviewVisualPolicy.PLACEMENT_PULSE_MIN_ALPHA
        val max = PreviewVisualPolicy.PLACEMENT_PULSE_MAX_ALPHA

        assertTrue(min >= 8, "placement sides need a visible floor")
        assertTrue(max >= 24, "placement sides need a prominent peak")
        assertEquals(min.toFloat(), PreviewVisualPolicy.pulseAlpha(min, max, 0f))
        assertEquals(max.toFloat(), PreviewVisualPolicy.pulseAlpha(min, max, -1f))
        assertEquals(max.toFloat(), PreviewVisualPolicy.pulseAlpha(min, max, 1f))
    }

    @Test
    fun `ghost shell culls back faces for GPU efficiency`() {
        assertTrue(PreviewVisualPolicy.CULL_GHOST_BACK_FACES)
    }
}

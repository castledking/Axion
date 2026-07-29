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
    fun `closed destination shell leaves approximately twenty percent of the scene visible`() {
        val alpha = PreviewVisualPolicy.alphaForTransmission(
            PreviewVisualPolicy.TARGET_SCENE_TRANSMISSION,
            PreviewVisualPolicy.CLOSED_SHELL_CROSSINGS,
        )

        assertEquals(141, alpha)
        assertEquals(alpha, PreviewVisualPolicy.DESTINATION_ALPHA)
        assertEquals(alpha, PreviewVisualPolicy.MOVE_DESTINATION_ALPHA)
        assertEquals(
            PreviewVisualPolicy.TARGET_SCENE_TRANSMISSION,
            PreviewVisualPolicy.compoundedTransmission(alpha, PreviewVisualPolicy.CLOSED_SHELL_CROSSINGS),
            absoluteTolerance = 0.002,
        )
    }

    @Test
    fun `move source glass stays translucent after the world cells are masked`() {
        assertTrue(PreviewVisualPolicy.MOVE_SOURCE_ALPHA < 128)
        assertEquals(
            PreviewVisualPolicy.MOVE_SOURCE_TARGET_SCENE_TRANSMISSION,
            PreviewVisualPolicy.compoundedTexturedTransmission(
                PreviewVisualPolicy.MOVE_SOURCE_ALPHA,
                PreviewVisualPolicy.STAINED_GLASS_MEAN_TEXTURE_ALPHA,
                PreviewVisualPolicy.CLOSED_SHELL_CROSSINGS,
            ),
            absoluteTolerance = 0.003,
        )
        assertEquals(
            PreviewVisualPolicy.MOVE_SOURCE_TARGET_SCENE_TRANSMISSION,
            PreviewVisualPolicy.compoundedTexturedTransmission(
                PreviewVisualPolicy.CULLED_MOVE_SOURCE_ALPHA,
                PreviewVisualPolicy.STAINED_GLASS_MEAN_TEXTURE_ALPHA,
                1,
            ),
            absoluteTolerance = 0.003,
        )
    }

    @Test
    fun `sparse shells keep twenty percent transmission across culling modes`() {
        val noCullExpected = PreviewVisualPolicy.alphaForTransmission(
            PreviewVisualPolicy.TARGET_SCENE_TRANSMISSION,
            PreviewVisualPolicy.CLOSED_SHELL_CROSSINGS,
        )
        val culledExpected = PreviewVisualPolicy.alphaForTransmission(
            PreviewVisualPolicy.TARGET_SCENE_TRANSMISSION,
            1,
        )

        assertEquals(141, noCullExpected)
        assertEquals(204, culledExpected)
        assertEquals(noCullExpected, PreviewVisualPolicy.SPARSE_DESTINATION_ALPHA)
        assertEquals(culledExpected, PreviewVisualPolicy.CULLED_DESTINATION_ALPHA)
        assertEquals(culledExpected, PreviewVisualPolicy.CULLED_SPARSE_DESTINATION_ALPHA)
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

package axion.client.render

import axion.client.render.gpu.PreviewOcclusionPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewOcclusionPolicyTest {
    @Test
    fun `non-occluding fluid neighbor leaves the touching block face visible`() {
        assertTrue(
            PreviewOcclusionPolicy.isFaceExposed("water") { neighbor ->
                neighbor == "solid"
            },
        )
    }

    @Test
    fun `opaque neighbor hides the touching block face`() {
        assertFalse(
            PreviewOcclusionPolicy.isFaceExposed("solid") { neighbor ->
                neighbor == "solid"
            },
        )
    }

    @Test
    fun `missing neighbor leaves the block face visible`() {
        assertTrue(
            PreviewOcclusionPolicy.isFaceExposed<String>(null) { true },
        )
    }

    @Test
    fun `only cardinally adjacent blocks participate in face occlusion`() {
        assertTrue(
            PreviewOcclusionPolicy.isDirectNeighbor(
                x = 11,
                y = 20,
                z = 30,
                originX = 10,
                originY = 20,
                originZ = 30,
            ),
        )
        assertFalse(
            PreviewOcclusionPolicy.isDirectNeighbor(
                x = 11,
                y = 21,
                z = 30,
                originX = 10,
                originY = 20,
                originZ = 30,
            ),
        )
    }
}

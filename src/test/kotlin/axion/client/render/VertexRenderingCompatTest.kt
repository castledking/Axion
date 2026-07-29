package axion.client.render

import kotlin.test.Test
import kotlin.test.assertEquals

class VertexRenderingCompatTest {
    @Test
    fun `manual outline applies the same signed offset as vanilla shape rendering`() {
        val worldCoordinate = 100.0
        val negativeCameraOffset = -90.0

        assertEquals(
            10.0f,
            VertexRenderingCompat.outlineCoordinate(worldCoordinate, negativeCameraOffset),
            "small Magic Select outlines must land at world minus camera",
        )
    }
}

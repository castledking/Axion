package axion.client.render.gpu

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewSectionTransformTest {
    @Test
    fun sectionLocalMeshUsesCameraRelativeWorldTranslation() {
        val translation = PreviewSectionTransform.cameraRelative(
            sectionOriginX = 96,
            sectionOriginY = 64,
            sectionOriginZ = -48,
            cameraX = 91.25,
            cameraY = 70.5,
            cameraZ = -54.75,
            deltaX = 3,
            deltaY = -2,
            deltaZ = 5,
        )

        assertEquals(7.75f, translation.x)
        assertEquals(-8.5f, translation.y)
        assertEquals(11.75f, translation.z)
    }
}

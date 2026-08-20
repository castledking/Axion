package axion.client.render.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewTranslucencySortPolicyTest {
    @Test
    fun sectionsAcrossMultipleAxesShareOneGlobalSortBatch() {
        val plan = PreviewTranslucencySortPolicy.globalMeshPlan(
            listOf(
                PreviewTranslucencySortPolicy.SectionOrigin(32, 48, -16),
                PreviewTranslucencySortPolicy.SectionOrigin(-16, 80, 32),
                PreviewTranslucencySortPolicy.SectionOrigin(16, 64, 0),
            ),
        )

        assertEquals(PreviewTranslucencySortPolicy.SectionOrigin(-16, 48, -16), plan?.anchor)
        assertEquals(3, plan?.sectionCount)
        assertEquals(1, plan?.batchCount)
    }

    @Test
    fun movingPreviewChangesEffectiveCameraForQuadSort() {
        val point = PreviewTranslucencySortPolicy.effectiveCamera(
            cameraX = 40.25,
            cameraY = 70.5,
            cameraZ = -12.75,
            deltaX = 5,
            deltaY = -2,
            deltaZ = 3,
        )

        assertEquals(35.25f, point.x)
        assertEquals(72.5f, point.y)
        assertEquals(-15.75f, point.z)
    }

    @Test
    fun quadIndicesResortAfterMeaningfulCameraTranslationOnly() {
        assertFalse(
            PreviewTranslucencySortPolicy.shouldResort(
                previousX = 4.0f,
                previousY = 5.0f,
                previousZ = 6.0f,
                currentX = 4.25f,
                currentY = 5.0f,
                currentZ = 6.0f,
            ),
        )
        assertTrue(
            PreviewTranslucencySortPolicy.shouldResort(
                previousX = 4.0f,
                previousY = 5.0f,
                previousZ = 6.0f,
                currentX = 5.0f,
                currentY = 5.0f,
                currentZ = 6.0f,
            ),
        )
        assertTrue(
            PreviewTranslucencySortPolicy.shouldResort(
                previousX = 4.9f,
                previousY = 5.0f,
                previousZ = 6.0f,
                currentX = 5.1f,
                currentY = 5.0f,
                currentZ = 6.0f,
            ),
        )
        assertTrue(
            PreviewTranslucencySortPolicy.shouldResort(
                previousX = Float.NaN,
                previousY = Float.NaN,
                previousZ = Float.NaN,
                currentX = 4.0f,
                currentY = 5.0f,
                currentZ = 6.0f,
            ),
        )
    }
}

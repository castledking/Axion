package axion.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitySelectionMaskTest {
    @Test
    fun `sparse selection includes entities on blobs but excludes gaps in their bounds`() {
        val mask = EntitySelectionMask.fromSelectedOffsets(
            sourceSize = IntVector3(9, 2, 1),
            selectedOffsets = listOf(
                IntVector3(0, 0, 0),
                IntVector3(8, 0, 0),
                IntVector3(8, 1, 0),
            ),
        )
        val matcher = mask.matcher(
            sourceMin = IntVector3(10, 20, 30),
            sourceMax = IntVector3(18, 21, 30),
        )

        assertTrue(matcher.containsFeet(10.5, 21.0, 30.5))
        assertTrue(matcher.containsFeet(18.5, 21.4, 30.5))
        assertFalse(matcher.containsFeet(14.5, 21.0, 30.5))
    }

    @Test
    fun `repeating a sparse selection preserves gaps and normalizes the folded bounds`() {
        val source = EntitySelectionMask.fromSelectedOffsets(
            sourceSize = IntVector3(3, 1, 1),
            selectedOffsets = listOf(IntVector3(0, 0, 0), IntVector3(2, 0, 0)),
        )

        val repeated = source.repeated(
            sourceSize = IntVector3(3, 1, 1),
            step = IntVector3(3, 0, 0),
            repeatCount = 1,
        )
        val matcher = repeated.mask.matcher(
            sourceMin = IntVector3(0, 0, 0),
            sourceMax = IntVector3(5, 0, 0),
        )

        assertEquals(IntVector3(0, 0, 0), repeated.relativeOrigin)
        assertEquals(IntVector3(6, 1, 1), repeated.size)
        assertTrue(matcher.containsFeet(0.5, 1.0, 0.5))
        assertTrue(matcher.containsFeet(3.5, 1.0, 0.5))
        assertFalse(matcher.containsFeet(1.5, 1.0, 0.5))
        assertFalse(matcher.containsFeet(4.5, 1.0, 0.5))
    }

    @Test
    fun `full cuboid coverage is represented without serialized offsets`() {
        val mask = EntitySelectionMask.fromSelectedOffsets(
            sourceSize = IntVector3(2, 1, 2),
            selectedOffsets = listOf(
                IntVector3(0, 0, 0),
                IntVector3(0, 0, 1),
                IntVector3(1, 0, 0),
                IntVector3(1, 0, 1),
            ),
        )

        assertEquals(EntitySelectionMode.FULL_REGION, mask.mode)
        assertEquals(emptyList(), mask.offsets)
        assertEquals(4L, mask.selectedBlockCount(IntVector3(2, 1, 2)))
    }

    @Test
    fun `adjacent repeats of a full region recompress to full`() {
        val repeated = EntitySelectionMask.fullRegion().repeated(
            sourceSize = IntVector3(3, 1, 1),
            step = IntVector3(3, 0, 0),
            repeatCount = 2,
        )

        assertEquals(IntVector3(9, 1, 1), repeated.size)
        assertEquals(EntitySelectionMode.FULL_REGION, repeated.mask.mode)
        assertEquals(emptyList(), repeated.mask.offsets)
    }

    @Test
    fun `destination policy positions use the same mirror then rotation transform as blocks`() {
        val mask = EntitySelectionMask.sparseOffsets(listOf(IntVector3(0, 0, 0)))

        assertEquals(
            listOf(IntVector3(12, 5, 11)),
            EntitySelectionGeometry.destinationPositions(
                sourceMin = IntVector3(1, 2, 3),
                sourceMax = IntVector3(2, 2, 5),
                destinationOrigin = IntVector3(10, 5, 10),
                rotationQuarterTurns = 1,
                mirrorAxis = PlacementMirrorAxisPayload.X,
                mask = mask,
            ).toList(),
        )
    }

    @Test
    fun `sparse offsets outside source dimensions are invalid`() {
        val mask = EntitySelectionMask.sparseOffsets(listOf(IntVector3(3, 0, 0)))

        assertFalse(mask.isValidFor(IntVector3(3, 1, 1)))
    }
}

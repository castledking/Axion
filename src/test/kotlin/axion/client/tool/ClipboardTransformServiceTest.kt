package axion.client.tool

import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.Vec3i
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardTransformServiceTest {
    @Test
    fun `clockwise rotation swaps x and z extents`() {
        val buffer = ClipboardBuffer(
            size = Vec3i(2, 3, 4),
            cells = emptyList(),
        )

        val transformed = ClipboardTransformService.transform(
            buffer,
            PlacementTransform(rotationQuarterTurns = 1),
        )

        assertEquals(Vec3i(4, 3, 2), transformed.size)
    }

    @Test
    fun `mirror then rotate remaps offsets into transformed bounds`() {
        val transformedOffset = ClipboardTransformService.transformedOffset(
            size = Vec3i(2, 1, 3),
            offset = Vec3i(0, 0, 0),
            PlacementTransform(rotationQuarterTurns = 1, mirrored = true),
        )

        assertEquals(Vec3i(2, 0, 1), transformedOffset)
    }
}

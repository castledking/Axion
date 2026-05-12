package axion.client.tool

import net.minecraft.util.math.Vec3i
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionRepeatPlacementServiceTest {
    @Test
    fun `smear offsets follow rounded 3d line to node`() {
        val offsets = RegionRepeatPlacementService.smearOffsets(Vec3i(3, 3, 0))

        assertEquals(
            listOf(
                Vec3i(1, 1, 0),
                Vec3i(2, 2, 0),
                Vec3i(3, 3, 0),
            ),
            offsets,
        )
    }

    @Test
    fun `smear offsets preserve shallow diagonals`() {
        val offsets = RegionRepeatPlacementService.smearOffsets(Vec3i(4, 2, 0))

        assertEquals(
            listOf(
                Vec3i(1, 1, 0),
                Vec3i(2, 1, 0),
                Vec3i(3, 2, 0),
                Vec3i(4, 2, 0),
            ),
            offsets,
        )
    }
}

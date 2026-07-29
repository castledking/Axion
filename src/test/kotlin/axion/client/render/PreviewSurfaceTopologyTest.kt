package axion.client.render

import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PreviewSurfaceTopologyTest {
    @Test
    fun `deep translucent prism omits cells enclosed by occupancy`() {
        val occupied = buildList {
            for (x in 0..2) for (y in 0 until 32) for (z in 0..2) {
                add(BlockPos.asLong(x, y, z))
            }
        }.toLongArray()

        val surface = PreviewSurfaceTopology.retainBoundaryOffsets(occupied)

        assertEquals(258, surface.size)
        assertContentEquals(
            longArrayOf(BlockPos.asLong(1, 0, 1), BlockPos.asLong(1, 31, 1)),
            surface.filter { packed ->
                BlockPos.fromLong(packed).let { pos -> pos.x == 1 && pos.z == 1 }
            }.toLongArray(),
        )
    }

    @Test
    fun `solid cube omits its center regardless of block opacity`() {
        val occupied = buildList {
            for (x in 0..2) for (y in 0..2) for (z in 0..2) {
                add(BlockPos.asLong(x, y, z))
            }
        }.toLongArray()

        val surface = PreviewSurfaceTopology.retainBoundaryOffsets(occupied)

        assertEquals(26, surface.size)
    }
}

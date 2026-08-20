package axion.client.render

import axion.common.model.BlockRegion
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RepeatPreviewLayoutTest {
    @Test
    fun destinationRegionsRespectsLimit() {
        val source = BlockRegion(BlockPos(0, 0, 0), BlockPos(1, 1, 1))

        val regions = RepeatPreviewLayout.destinationRegions(
            sourceRegion = source,
            step = Vec3i(2, 0, 0),
            repeatCount = 10,
            maxRegions = 3,
        )

        assertEquals(3, regions.size)
        assertEquals(BlockPos(2, 0, 0), regions.first().minCorner())
        assertEquals(BlockPos(6, 0, 0), regions.last().minCorner())
    }

    @Test
    fun aggregateRegionSpansHiddenRepeats() {
        val source = BlockRegion(BlockPos(0, 0, 0), BlockPos(1, 1, 1))

        val aggregate = RepeatPreviewLayout.aggregateRegion(
            sourceRegion = source,
            step = Vec3i(2, 0, 0),
            startIndex = 4,
            endIndex = 6,
        )

        assertNotNull(aggregate)
        assertEquals(BlockPos(8, 0, 0), aggregate.minCorner())
        assertEquals(BlockPos(13, 1, 1), aggregate.maxCorner())
    }
}

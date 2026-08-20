package axion.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import net.minecraft.util.math.BlockPos

class BlockRegionTest {
    @Test
    fun `expand face does not shrink when target stays inside region`() {
        val region = BlockRegion(BlockPos(0, 0, 0), BlockPos(4, 4, 4))

        assertEquals(region, region.expandFace(RegionFace.WEST, BlockPos(2, 2, 2)))
        assertEquals(region, region.expandFace(RegionFace.EAST, BlockPos(2, 2, 2)))
        assertEquals(region, region.expandFace(RegionFace.DOWN, BlockPos(2, 2, 2)))
        assertEquals(region, region.expandFace(RegionFace.UP, BlockPos(2, 2, 2)))
        assertEquals(region, region.expandFace(RegionFace.NORTH, BlockPos(2, 2, 2)))
        assertEquals(region, region.expandFace(RegionFace.SOUTH, BlockPos(2, 2, 2)))
    }

    @Test
    fun `expand face grows outward to outside target`() {
        val region = BlockRegion(BlockPos(0, 0, 0), BlockPos(4, 4, 4))

        assertEquals(
            BlockRegion(BlockPos(-3, 0, 0), BlockPos(4, 4, 4)),
            region.expandFace(RegionFace.WEST, BlockPos(-3, 2, 2)),
        )
        assertEquals(
            BlockRegion(BlockPos(0, 0, 0), BlockPos(7, 4, 4)),
            region.expandFace(RegionFace.EAST, BlockPos(7, 2, 2)),
        )
        assertEquals(
            BlockRegion(BlockPos(0, -2, 0), BlockPos(4, 4, 4)),
            region.expandFace(RegionFace.DOWN, BlockPos(2, -2, 2)),
        )
        assertEquals(
            BlockRegion(BlockPos(0, 0, 0), BlockPos(4, 9, 4)),
            region.expandFace(RegionFace.UP, BlockPos(2, 9, 2)),
        )
    }

    @Test
    fun `extend face grows exactly one block outward`() {
        val region = BlockRegion(BlockPos(0, 0, 0), BlockPos(1, 1, 1))

        assertEquals(
            BlockRegion(BlockPos(-1, 0, 0), BlockPos(1, 1, 1)),
            region.extendFace(RegionFace.WEST),
        )
        assertEquals(
            BlockRegion(BlockPos(0, 0, 0), BlockPos(1, 2, 1)),
            region.extendFace(RegionFace.UP),
        )
        assertEquals(
            BlockRegion(BlockPos(0, 0, 0), BlockPos(1, 1, 2)),
            region.extendFace(RegionFace.SOUTH),
        )
    }

    @Test
    fun `resize face grows selected face and planar axes toward outside target`() {
        val region = BlockRegion(BlockPos(0, 0, 0), BlockPos(4, 4, 4))

        assertEquals(
            BlockRegion(BlockPos(0, -2, 0), BlockPos(7, 4, 8)),
            region.resizeFaceToward(RegionFace.EAST, BlockPos(7, -2, 8)),
        )
        assertEquals(
            BlockRegion(BlockPos(-3, 0, -1), BlockPos(4, 7, 4)),
            region.resizeFaceToward(RegionFace.WEST, BlockPos(-3, 7, -1)),
        )
    }

    @Test
    fun `resize face contracts selected face toward inside target`() {
        val region = BlockRegion(BlockPos(0, 0, 0), BlockPos(4, 4, 4))

        assertEquals(
            BlockRegion(BlockPos(0, 0, 0), BlockPos(2, 4, 4)),
            region.resizeFaceToward(RegionFace.EAST, BlockPos(2, 2, 2)),
        )
        assertEquals(
            BlockRegion(BlockPos(2, 0, 0), BlockPos(4, 4, 4)),
            region.resizeFaceToward(RegionFace.WEST, BlockPos(2, 2, 2)),
        )
        assertEquals(
            BlockRegion(BlockPos(0, 3, 0), BlockPos(4, 4, 4)),
            region.resizeFaceToward(RegionFace.DOWN, BlockPos(2, 3, 2)),
        )
    }

    @Test
    fun `remap corner keeps first corner on expanded west face`() {
        val original = BlockRegion(BlockPos(0, 10, 0), BlockPos(4, 14, 4))
        val expanded = BlockRegion(BlockPos(-1, 10, 0), BlockPos(4, 14, 4))

        assertEquals(
            BlockPos(-1, 10, 0),
            original.remapCorner(BlockPos(0, 10, 0), expanded),
        )
        assertEquals(
            BlockPos(4, 14, 4),
            original.remapCorner(BlockPos(4, 14, 4), expanded),
        )
    }

    @Test
    fun `remap corner recovers interior anchor toward nearest boundary`() {
        val original = BlockRegion(BlockPos(0, 0, 0), BlockPos(4, 4, 4))
        val expanded = BlockRegion(BlockPos(-1, 0, 0), BlockPos(4, 4, 4))

        assertEquals(
            BlockPos(-1, 0, 0),
            original.remapCorner(BlockPos(1, 0, 0), expanded),
        )
    }
}

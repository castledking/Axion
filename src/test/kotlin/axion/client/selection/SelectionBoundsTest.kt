package axion.client.selection

import axion.common.model.BlockRegion
import axion.common.model.RegionFace
import kotlin.test.Test
import kotlin.test.assertEquals
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

class SelectionBoundsTest {
    private val region = BlockRegion(BlockPos(0, 0, 0), BlockPos(2, 2, 2))

    @Test
    fun `raycast face picks west entry face`() {
        val hit = SelectionBounds.raycastFace(
            region = region,
            origin = Vec3d(-3.0, 1.5, 1.5),
            direction = Vec3d(1.0, 0.0, 0.0),
            maxDistance = 16.0,
        )

        assertEquals(RegionFace.WEST, hit?.face)
        assertEquals(Vec3d(0.0, 1.5, 1.5), hit?.point)
    }

    @Test
    fun `raycast face picks up face from above`() {
        val hit = SelectionBounds.raycastFace(
            region = region,
            origin = Vec3d(1.5, 5.0, 1.5),
            direction = Vec3d(0.0, -1.0, 0.0),
            maxDistance = 16.0,
        )

        assertEquals(RegionFace.UP, hit?.face)
        assertEquals(Vec3d(1.5, 3.0, 1.5), hit?.point)
    }

    @Test
    fun `raycast face uses exit face when camera starts inside region`() {
        val hit = SelectionBounds.raycastFace(
            region = region,
            origin = Vec3d(1.5, 1.5, 1.5),
            direction = Vec3d(0.0, 0.0, 1.0),
            maxDistance = 16.0,
        )

        assertEquals(RegionFace.SOUTH, hit?.face)
        assertEquals(Vec3d(1.5, 1.5, 3.0), hit?.point)
    }

    @Test
    fun `outward face toward prefers clicked side over unrelated downward axis`() {
        val face = SelectionBounds.outwardFaceToward(
            region = region,
            target = BlockPos(-1, -1, 1),
            point = Vec3d(-0.6, 0.9, 1.5),
        )

        assertEquals(RegionFace.WEST, face)
    }

    @Test
    fun `outward face toward chooses up when target is only above region`() {
        val face = SelectionBounds.outwardFaceToward(
            region = region,
            target = BlockPos(1, 4, 1),
            point = Vec3d(1.4, 4.0, 1.6),
        )

        assertEquals(RegionFace.UP, face)
    }

    @Test
    fun `outward face toward prefers adjacent side over vertical offset`() {
        val face = SelectionBounds.outwardFaceToward(
            region = region,
            target = BlockPos(-1, 3, 1),
            point = Vec3d(-0.8, 3.95, 1.5),
        )

        assertEquals(RegionFace.WEST, face)
    }

    @Test
    fun `outward face toward prefers horizontal face in equal gap tie`() {
        val face = SelectionBounds.outwardFaceToward(
            region = region,
            target = BlockPos(-1, -1, 1),
            point = Vec3d(-0.2, 0.1, 1.5),
        )

        assertEquals(RegionFace.WEST, face)
    }
}

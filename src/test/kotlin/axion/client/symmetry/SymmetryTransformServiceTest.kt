package axion.client.symmetry

import axion.common.model.SymmetryMirrorAxis
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals

class SymmetryTransformServiceTest {
    @Test
    fun `rotation transforms horizontal placement face`() {
        val transformed = SymmetryTransformService.transformDirection(
            direction = Direction.EAST,
            transform = SymmetryTransformSpec(rotationQuarterTurns = 1),
        )

        assertEquals(Direction.SOUTH, transformed)
    }

    @Test
    fun `x mirror transforms horizontal placement face`() {
        val transformed = SymmetryTransformService.transformDirection(
            direction = Direction.EAST,
            transform = SymmetryTransformSpec(rotationQuarterTurns = 0, mirrorAxis = SymmetryMirrorAxis.X),
        )

        assertEquals(Direction.WEST, transformed)
    }

    @Test
    fun `mixed half-grid anchor rotates blocks without flooring drift`() {
        val transformed = SymmetryTransformService.transformBlock(
            sourceBlock = BlockPos(0, 0, 0),
            anchor = Vec3d(0.0, 0.5, 0.5),
            transform = SymmetryTransformSpec(rotationQuarterTurns = 1),
        )

        assertEquals(BlockPos(-1, 0, 0), transformed)
    }
}

package axion.client.symmetry

import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

class SymmetryTransformServiceTest {
    @Test
    fun `rotation transforms horizontal placement face`() {
        val transformed = SymmetryTransformService.transformDirection(
            direction = Direction.EAST,
            transform = SymmetryTransformSpec(rotationQuarterTurns = 1, mirrorY = false),
        )

        assertEquals(Direction.SOUTH, transformed)
    }

    @Test
    fun `mirror transforms vertical placement face`() {
        val transformed = SymmetryTransformService.transformDirection(
            direction = Direction.UP,
            transform = SymmetryTransformSpec(rotationQuarterTurns = 0, mirrorY = true),
        )

        assertEquals(Direction.DOWN, transformed)
    }
}

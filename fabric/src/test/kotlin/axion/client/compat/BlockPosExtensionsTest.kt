package axion.client.compat

import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockPosExtensionsTest {
    @Test
    fun `positions retained from a cuboid iterator remain distinct`() {
        val retained = blockPosIterate(
            BlockPos(4, 8, 12),
            BlockPos(6, 8, 12),
        ).map { it.toImmutable() }

        assertEquals(
            listOf(
                BlockPos(4, 8, 12),
                BlockPos(5, 8, 12),
                BlockPos(6, 8, 12),
            ),
            retained,
        )
    }
}

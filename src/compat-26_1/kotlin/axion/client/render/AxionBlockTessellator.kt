package axion.client.render

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Legacy tessellator adapter retained for older preview call sites.
 *
 * The 26.1.x renderer builds chunked GPU preview sessions directly, so this
 * adapter reports unsupported instead of attempting immediate-mode tessellation.
 */

object AxionBlockTessellator {
    fun clearCache() {
    }

    @Suppress("UNUSED_PARAMETER")
    fun tessellateBlock(
        state: BlockState,
        pos: BlockPos,
        world: Level,
        matrixStack: Any,
        consumer: Any,
        checkSides: Boolean = true,
        cameraX: Double = 0.0,
        cameraY: Double = 0.0,
        cameraZ: Double = 0.0,
        scale: Float = 1.0f,
    ): Boolean {
        return false
    }

    @Suppress("UNUSED_PARAMETER")
    fun tessellateRegion(
        statesByPosition: Map<Long, BlockState>,
        world: Level,
        matrixStack: Any,
        consumer: Any,
    ): Boolean {
        return false
    }
}

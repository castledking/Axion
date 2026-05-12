package axion.client.render

import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.BlockPos

object GhostBlockPreviewRenderer {
    private const val MAX_GHOST_BLOCKS: Int = 65536

    fun maxOriginsFor(nonAirCellCount: Int): Int {
        if (nonAirCellCount <= 0) {
            return 0
        }
        return MAX_GHOST_BLOCKS / nonAirCellCount
    }

    fun render(
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int = 0xFFFFFFFF.toInt(),
        alpha: Int = 44,
        textured: Boolean = false,
        fullBlock: Boolean = false,
        scale: Float = 1.0f,
        sessionTag: String = "default",
        forceChunked: Boolean = false,
    ) {
        // Block tessellation not yet ported to 26.1.2
    }
}

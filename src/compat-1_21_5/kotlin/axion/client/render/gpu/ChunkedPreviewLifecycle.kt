package axion.client.render.gpu

import axion.client.render.AxionWorldRenderContext
import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.BlockPos

object ChunkedPreviewLifecycle {
    fun acquire(id: String): ChunkedPreviewSession {
        return ChunkedPreviewSession
    }

    fun release(id: String) {
    }

    fun clear() {
    }
}

object ChunkedPreviewSession {
    fun setFromClipboard(clipboard: ClipboardBuffer, origins: Collection<BlockPos>): Boolean {
        return false
    }

    fun render(context: AxionWorldRenderContext, color: Int, alpha: Int) {
    }
}

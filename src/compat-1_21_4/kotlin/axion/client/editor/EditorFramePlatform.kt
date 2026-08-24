package axion.client.editor

import com.mojang.blaze3d.platform.GlStateManager

/**
 * Yarn-range implementation: classic immediate GL, viewport pushes stick for
 * the whole world + vanilla HUD phase.
 */
object EditorFramePlatform {
    val supportsFraming: Boolean = true

    fun pushFrameViewport() {
        val f = EditorFrameController
        GlStateManager._viewport(
            f.frameXpx,
            f.frameYpxGl,
            f.frameWidthPx,
            f.frameHeightPx,
        )
    }

    fun popFrameViewport() {
        val f = EditorFrameController
        GlStateManager._viewport(0, 0, f.realFramebufferWidth, f.realFramebufferHeight)
    }
}

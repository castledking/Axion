package axion.client.editor

/**
 * Official-mapping ranges (26.x): the render pass system owns viewport and
 * scissor state per pass (RenderArea / ScissorState), so global viewport
 * pushes do not stick. Framing stays disabled here until the pass-area route
 * is wired; the editor falls back to the full-window overlay.
 */
object EditorFramePlatform {
    val supportsFraming: Boolean = false

    fun pushFrameViewport() {
    }

    fun popFrameViewport() {
    }
}

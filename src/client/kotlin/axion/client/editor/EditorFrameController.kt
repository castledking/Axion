package axion.client.editor

/**
 * Geometry + viewport math for the Axiom-style squished game frame.
 *
 * While framing is active, vanilla is told the framebuffer is only the inner
 * rectangle between the editor panels (via the window mixin), so chat, HUD
 * and screens lay out inside the frame exactly like Axiom. The renderer mixin
 * scopes nothing itself — the small framebuffer IS the frame; [AxionEditorUi]
 * draws chrome over the full window margins on top.
 *
 * The controller caches the REAL window/framebuffer dimensions (read before
 * any lie applies) so the overlay keeps sizing itself to the true window.
 */
object EditorFrameController {
    const val TOP_RESERVED: Int = AxionLayout.TOP_BAR_HEIGHT + AxionLayout.VIEWS_BAR_HEIGHT + 4
    const val BOTTOM_RESERVED: Int = AxionLayout.BOTTOM_BAR_HEIGHT + 4
    const val LEFT_RESERVED: Int = 8 + AxionLayout.PANEL_WIDTH + 8
    const val RIGHT_RESERVED: Int = 8 + AxionLayout.PANEL_WIDTH + 8

    // Real (un-lied) dimensions, refreshed by the window mixin each read.
    var realFramebufferWidth: Int = 1
        private set
    var realFramebufferHeight: Int = 1
        private set
    var realWindowWidth: Int = 1
        private set
    var realWindowHeight: Int = 1
        private set

    /** Frame rectangle in full-window GUI units (overlay coordinate space). */
    var frameX: Int = 0
        private set
    var frameY: Int = 0
        private set
    var frameWidthGui: Int = 1
        private set
    var frameHeightGui: Int = 1
        private set

    // Frame rectangle in framebuffer pixels (derived from the GUI rect).
    var frameXpx: Int = 0
        private set
    var frameYpxGl: Int = 0
        private set
    var frameWidthPx: Int = 1
        private set
    var frameHeightPx: Int = 1
        private set

    /**
     * Refreshes geometry. Called by the window mixin with the REAL shadowed
     * values before any override returns; also drives the pixel conversion
     * using the framebuffer/window ratio as the effective GUI scale.
     */
    var realScaledWidth: Int = 1
        private set
    var realScaledHeight: Int = 1
        private set

    fun update(
        framebufferWidth: Int,
        framebufferHeight: Int,
        windowWidth: Int,
        windowHeight: Int,
        scaledWidth: Int,
        scaledHeight: Int,
    ) {
        realScaledWidth = scaledWidth
        realScaledHeight = scaledHeight
        if (windowWidth <= 0 || windowHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0) {
            return
        }
        this.realFramebufferWidth = framebufferWidth
        this.realFramebufferHeight = framebufferHeight
        this.realWindowWidth = windowWidth
        this.realWindowHeight = windowHeight

        val scaleW = framebufferWidth.toFloat() / windowWidth.toFloat()
        val scaleH = framebufferHeight.toFloat() / windowHeight.toFloat()

        frameX = LEFT_RESERVED
        frameY = TOP_RESERVED
        frameWidthGui = (windowWidth - LEFT_RESERVED - RIGHT_RESERVED).coerceAtLeast(64)
        frameHeightGui = (windowHeight - TOP_RESERVED - BOTTOM_RESERVED).coerceAtLeast(64)

        frameXpx = (frameX * scaleW).toInt().coerceIn(0, framebufferWidth - 2)
        frameWidthPx = (frameWidthGui * scaleW).toInt().coerceIn(2, framebufferWidth - frameXpx)
        frameHeightPx = (frameHeightGui * scaleH).toInt().coerceIn(2, framebufferHeight)
        frameYpxGl = (framebufferHeight - frameY * scaleH - frameHeightPx).toInt()
            .coerceIn(0, framebufferHeight - frameHeightPx)
    }
}

package axion.client.input

object AxionShortcutPreemptionPolicy {
    fun shouldSuppressOffhandSwap(
        controlDown: Boolean,
        mirrorKeyDown: Boolean,
        canFlipPreview: Boolean,
        canToggleMirror: Boolean,
    ): Boolean {
        return controlDown && mirrorKeyDown && (canFlipPreview || canToggleMirror)
    }
}

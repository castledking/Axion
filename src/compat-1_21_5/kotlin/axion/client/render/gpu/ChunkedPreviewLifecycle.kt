package axion.client.render.gpu

/**
 * 1.21.5 intentionally uses the CPU preview path.
 *
 * Keep this object as a no-op compatibility hook because shared render
 * callback code calls it after batched immediate rendering. The GPU preview
 * uploader/drawer classes are excluded from the 1.21.5 release artifact.
 */
object ChunkedPreviewLifecycle {
    fun closeAll() {}

    fun flushDeferredDraws() {}
}

package axion.client.render

import axion.client.render.gpu.ChunkedPreviewLifecycle

object AxionPreviewBufferCache {
    fun invalidate() {
        ChunkedPreviewLifecycle.closeAll()
    }

    fun invalidateForClipboard() {
        invalidate()
    }

    fun invalidateForTemplate() {
        invalidate()
    }
}

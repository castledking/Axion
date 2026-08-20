package axion.client.render

import axion.client.render.gpu.ChunkedPreviewLifecycle

object AxionPreviewTemplateCache {
    fun getTemplate(templateId: String): Any? = null

    fun clear() {
        invalidate()
    }

    fun invalidate() {
        ChunkedPreviewLifecycle.closeAll()
    }
}

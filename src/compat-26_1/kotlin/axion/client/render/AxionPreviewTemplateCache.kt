package axion.client.render

/**
 * 26.1.2 specific stub implementation of AxionPreviewTemplateCache
 * GPU rendering API has changed significantly in 26.1.2
 * This is a stub to allow compilation - full implementation needed
 */

object AxionPreviewTemplateCache {
    fun getTemplate(templateId: String): Any? {
        // Stub - return null for now
        return null
    }
    
    fun clear() {
        // Stub - clear cache
    }
    
    fun invalidate() {
        // Stub - called from AxionServerConnection
    }
}

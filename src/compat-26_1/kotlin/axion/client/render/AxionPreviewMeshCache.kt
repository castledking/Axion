package axion.client.render

/**
 * Legacy mesh-cache adapter retained for old preview call sites.
 *
 * The 26.1.x preview path uses ChunkedPreviewLifecycle and does not populate
 * per-block cached meshes through this object.
 */

object AxionPreviewMeshCache {
    @Suppress("UNUSED_PARAMETER")
    fun getMesh(state: Any, pos: Any): Any? {
        return null
    }
    
    fun clear() {
    }
}

/**
 * Manages persistent GPU buffers for preview meshes.
 */
package axion.client.render

import axion.common.model.ClipboardBuffer

object AxionPreviewBufferCache {
    fun invalidate() {
        AxionPreviewMeshCache.invalidate()
    }

    fun invalidateForClipboard(clipboard: ClipboardBuffer) {
        AxionPreviewMeshCache.invalidateForClipboard(clipboard)
    }
}

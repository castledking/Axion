package axion.client.compat

import axion.client.render.gpu.AxionPreviewBlockDrawer
import axion.client.render.gpu.ChunkedPreviewLifecycle
import java.util.concurrent.atomic.AtomicBoolean
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

/**
 * Uploaded preview meshes contain baked-model UVs, so they cannot survive an
 * atlas/model reload (notably a server-provided resource pack).
 */
internal object PreviewResourceReloadInvalidation {
    private val listenerId = Identifier.fromNamespaceAndPath("axion", "preview_mesh_invalidation")
    private val registered = AtomicBoolean()

    fun register() {
        if (!registered.compareAndSet(false, true)) return

        val resourceLoader = ResourceLoader.get(PackType.CLIENT_RESOURCES)
        resourceLoader.registerReloadListener(
            listenerId,
            ResourceManagerReloadListener {
                ChunkedPreviewLifecycle.closeAll()
                AxionPreviewBlockDrawer.resetFailureState()
            },
        )
        resourceLoader.addListenerOrdering(ResourceReloaderKeys.AFTER_VANILLA, listenerId)
    }
}

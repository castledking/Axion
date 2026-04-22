package axion.client.render

import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BuiltBuffer
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.BufferAllocator
import net.minecraft.util.math.BlockPos
import java.util.LinkedHashMap

/**
 * Manages persistent GPU buffers for preview meshes. When a preview's
 * clipboard/origins/color/alpha change, the mesh is re-tessellated and
 * re-uploaded to the GPU. On subsequent frames with the same parameters,
 * the cached GPU buffer is drawn directly — no re-tessellation or upload.
 *
 * Lifecycle per cache entry:
 * 1. Cache miss → tessellate via AxionBlockTessellator into BufferBuilder
 * 2. End BufferBuilder → BuiltBuffer → upload to AxionPreviewBuffer (GPU)
 * 3. Each frame: draw from AxionPreviewBuffer via RenderPass
 * 4. Cache invalidation → close GPU buffer, remove entry
 */
object AxionPreviewBufferCache {
    private const val MAX_CACHE_SIZE = 16

    private data class BufferCacheKey(
        val clipboard: ClipboardBuffer?,
        val originKeys: List<Long>,
        val color: Int,
        val alpha: Int,
        val scale: Float,
        val writePositionKeys: List<Long>? = null,
    )

    data class BufferCacheEntry(
        val gpuBuffer: AxionPreviewBuffer,
        val renderLayer: RenderLayer,
    )

    private val cache = object : LinkedHashMap<BufferCacheKey, BufferCacheEntry>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BufferCacheKey, BufferCacheEntry>?): Boolean {
            if (size > MAX_CACHE_SIZE && eldest != null) {
                eldest.value.gpuBuffer.close()
                return true
            }
            return false
        }
    }

    /**
     * Get or create a cached GPU buffer for the given preview parameters.
     * Returns null if there are no blocks to render.
     */
    fun getOrUpload(
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int,
        alpha: Int,
        scale: Float,
        maxBlocks: Int = 1536,
    ): BufferCacheEntry? = synchronized(cache) {
        val mesh = AxionPreviewMeshCache.getOrBuild(clipboard, origins, color, alpha, scale, maxBlocks)
        if (mesh == null || mesh.blocks.isEmpty()) return null

        val maxOrigins = maxOf(1, maxBlocks / mesh.blocks.size.coerceAtLeast(1))
        val boundedOrigins = origins.asSequence().take(maxOrigins).toList()
        val originKeys = boundedOrigins.map { it.asLong() }.sorted()

        val key = BufferCacheKey(clipboard, originKeys, color, alpha, scale)

        cache.getOrPut(key) {
            val layer = RenderLayerCompat.blockTranslucentCull()
            val builtBuffer = tessellateToBuiltBuffer(mesh, layer, color, alpha)
            val gpuBuffer = AxionPreviewBuffer()
            if (builtBuffer != null) {
                gpuBuffer.upload(builtBuffer)
                builtBuffer.close()
            }
            BufferCacheEntry(gpuBuffer, layer)
        }
    }

    /**
     * Get or create a cached GPU buffer for a list of BlockWrite positions.
     * Used by renderTexturedWrites when clipboard-based caching isn't applicable.
     */
    fun getOrUploadForWrites(
        writes: List<PreviewBlockInfo>,
        color: Int,
        alpha: Int,
    ): BufferCacheEntry? = synchronized(cache) {
        if (writes.isEmpty()) return null

        val writePositionKeys = writes.map { it.pos.asLong() }.sorted()
        val key = BufferCacheKey(
            clipboard = null,
            originKeys = emptyList(),
            color = color,
            alpha = alpha,
            scale = 1.0f,
            writePositionKeys = writePositionKeys,
        )

        cache.getOrPut(key) {
            val layer = RenderLayerCompat.blockTranslucentCull()
            val mesh = AxionPreviewMeshCache.getOrBuildForWrites(writes, color, alpha)
            val builtBuffer = tessellateToBuiltBuffer(mesh, layer, color, alpha)
            val gpuBuffer = AxionPreviewBuffer()
            if (builtBuffer != null) {
                gpuBuffer.upload(builtBuffer)
                builtBuffer.close()
            }
            BufferCacheEntry(gpuBuffer, layer)
        }
    }

    /**
     * Draw a cached GPU buffer using a RenderPass configured from the render layer.
     * Follows the same pattern as RenderLayer.draw() but skips per-frame upload.
     */
    fun drawFromBuffer(entry: BufferCacheEntry) {
        // GPU pipeline draw path is disabled — uses MC-version-specific APIs
        // not available across all supported versions. Rendering is handled
        // via context.consumers() in GhostBlockPreviewRenderer instead.
    }

    fun invalidate() {
        synchronized(cache) {
            cache.values.forEach { it.gpuBuffer.close() }
            cache.clear()
        }
        AxionPreviewMeshCache.invalidate()
    }

    fun invalidateForClipboard(clipboard: ClipboardBuffer) {
        synchronized(cache) {
            val keysToRemove = cache.keys.filter { it.clipboard == clipboard }
            keysToRemove.forEach { key ->
                cache[key]?.gpuBuffer?.close()
                cache.remove(key)
            }
        }
        AxionPreviewMeshCache.invalidateForClipboard(clipboard)
    }

    private fun tessellateToBuiltBuffer(
        mesh: AxionPreviewMeshCache.CachedMesh,
        layer: RenderLayer,
        color: Int,
        alpha: Int,
    ): BuiltBuffer? {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return null
        val camera = client.gameRenderer.camera ?: return null
        val cameraPos = camera.cameraPos
        val alphaScale = alpha / 255.0f

        val previewView = AxionBlockTessellator.PreviewBlockRenderView(world, mesh.statesByPosition)
        val allocator = BufferAllocator(layer.expectedBufferSize)
        val bufferBuilder = BufferBuilder(allocator, layer.drawMode, layer.vertexFormat)
        val consumer = TintedAlphaVertexConsumer(bufferBuilder, alphaScale, color)

        val tessellateStack = net.minecraft.client.util.math.MatrixStack()

        AxionBlockTessellator.tessellateBatch(
            blocks = mesh.blocks,
            world = previewView,
            matrixStack = tessellateStack,
            consumer = consumer,
            cameraX = cameraPos.x,
            cameraY = cameraPos.y,
            cameraZ = cameraPos.z,
            checkSides = true,
        )

        return bufferBuilder.endNullable()
    }
}

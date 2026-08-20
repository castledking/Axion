package axion.client.render.gpu

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView

/** Reversed-Z depth copy retained across GameRenderer's hand-depth clear. */
internal class PreservedSceneDepth : AutoCloseable {
    private var texture: GpuTexture? = null
    private var textureView: GpuTextureView? = null
    private var width: Int = 0
    private var height: Int = 0

    fun capture(source: RenderTarget): GpuTextureView? {
        val sourceTexture = source.depthTexture ?: return null
        val sourceWidth = sourceTexture.getWidth(0)
        val sourceHeight = sourceTexture.getHeight(0)
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        ensureSize(sourceWidth, sourceHeight, sourceTexture.format)
        val destination = texture ?: return null
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            sourceTexture,
            destination,
            0,
            0,
            0,
            0,
            0,
            sourceWidth,
            sourceHeight,
        )
        return textureView
    }

    private fun ensureSize(
        requiredWidth: Int,
        requiredHeight: Int,
        requiredFormat: com.mojang.blaze3d.GpuFormat,
    ) {
        val current = texture
        if (
            current != null &&
            !current.isClosed &&
            current.format == requiredFormat &&
            width == requiredWidth &&
            height == requiredHeight
        ) return
        close()

        val device = RenderSystem.getDevice()
        val allocatedTexture = device.createTexture(
            "Axion preserved scene depth",
            DEPTH_TEXTURE_USAGE,
            requiredFormat,
            requiredWidth,
            requiredHeight,
            1,
            1,
        )
        val allocatedView = try {
            device.createTextureView(allocatedTexture)
        } catch (t: Throwable) {
            allocatedTexture.close()
            throw t
        }
        texture = allocatedTexture
        textureView = allocatedView
        width = requiredWidth
        height = requiredHeight
    }

    override fun close() {
        textureView?.close()
        texture?.close()
        textureView = null
        texture = null
        width = 0
        height = 0
    }

    private companion object {
        val DEPTH_TEXTURE_USAGE: Int =
            GpuTexture.USAGE_COPY_DST or
                GpuTexture.USAGE_RENDER_ATTACHMENT
    }
}

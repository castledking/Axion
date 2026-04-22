package axion.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.render.BuiltBuffer
import java.util.function.Supplier

/**
 * Persistent GPU buffer for preview meshes. Uploads vertex data once
 * and draws from the GPU buffer each frame, avoiding per-frame re-tessellation.
 *
 * Follows MC 1.21.11's ChunkRenderData.upload pattern:
 * - Uses GpuBuffer with USAGE_VERTEX / USAGE_INDEX
 * - Uploads via CommandEncoder.writeToBuffer or device.createBuffer
 * - Draws via RenderPass.setVertexBuffer / setIndexBuffer / drawIndexed
 */
class AxionPreviewBuffer : AutoCloseable {
    private var vertexBuffer: GpuBuffer? = null
    private var indexBuffer: GpuBuffer? = null
    private var indexCount: Int = 0
    private var indexType: VertexFormat.IndexType = VertexFormat.IndexType.SHORT
    private var vertexCount: Int = 0
    private var drawMode: VertexFormat.DrawMode = VertexFormat.DrawMode.TRIANGLES
    private var uploaded: Boolean = false

    val isUploaded: Boolean get() = uploaded

    /** Expose vertex GPU buffer for drawMultipleIndexed instanced draw path. */
    val vertexBufferGpu: GpuBuffer? get() = vertexBuffer?.takeUnless { it.isClosed }

    /** Expose index GPU buffer for drawMultipleIndexed instanced draw path. */
    val indexBufferGpu: GpuBuffer? get() = indexBuffer?.takeUnless { it.isClosed }

    /** Expose index type for drawMultipleIndexed instanced draw path. */
    val indexTypeValue: VertexFormat.IndexType get() = indexType

    /** Expose index count for drawMultipleIndexed instanced draw path. */
    val indexCountValue: Int get() = indexCount

    /**
     * Upload a BuiltBuffer to GPU. Follows ChunkRenderData.upload pattern:
     * - If existing buffer is too small, close and recreate
     * - If existing buffer fits, use CommandEncoder.writeToBuffer for in-place update
     */
    fun upload(builtBuffer: BuiltBuffer) {
        val params = builtBuffer.drawParameters
        val vertexData = builtBuffer.buffer
        val indexData = builtBuffer.sortedBuffer

        val device = RenderSystem.getDevice()
        val commandEncoder = device.createCommandEncoder()

        // Upload vertex buffer
        val existingVb = vertexBuffer
        if (existingVb == null || existingVb.isClosed || existingVb.size() < vertexData.remaining().toLong()) {
            vertexBuffer?.close()
            vertexBuffer = device.createBuffer(
                LABEL_VERTEX,
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_MAP_WRITE,
                vertexData,
            )
        } else if (!existingVb.isClosed) {
            commandEncoder.writeToBuffer(existingVb.slice(), vertexData)
        }

        // Upload index buffer
        if (indexData != null) {
            val existingIb = indexBuffer
            if (existingIb == null || existingIb.isClosed || existingIb.size() < indexData.remaining().toLong()) {
                indexBuffer?.close()
                indexBuffer = device.createBuffer(
                    LABEL_INDEX,
                    GpuBuffer.USAGE_INDEX or GpuBuffer.USAGE_MAP_WRITE,
                    indexData,
                )
            } else if (!existingIb.isClosed) {
                commandEncoder.writeToBuffer(existingIb.slice(), indexData)
            }
        }

        vertexCount = params.vertexCount
        indexCount = params.indexCount
        drawMode = params.mode
        indexType = params.indexType
        uploaded = true
    }

    /**
     * Draw the uploaded mesh using a RenderPass. The caller must configure
     * the render pass pipeline and texture bindings before calling this.
     */
    fun drawIndexed(renderPass: RenderPass) {
        val vb = vertexBuffer ?: return
        if (vb.isClosed) return

        renderPass.setVertexBuffer(0, vb)

        val ib = indexBuffer
        if (ib != null && !ib.isClosed && indexCount > 0) {
            renderPass.setIndexBuffer(ib, indexType)
            renderPass.drawIndexed(0, 0, indexCount, 1)
        } else if (vertexCount > 0) {
            renderPass.draw(0, vertexCount)
        }
    }

    override fun close() {
        vertexBuffer?.close()
        vertexBuffer = null
        indexBuffer?.close()
        indexBuffer = null
        uploaded = false
        indexCount = 0
        vertexCount = 0
    }

    companion object {
        private val LABEL_VERTEX: Supplier<String> = Supplier { "Axion Preview VB" }
        private val LABEL_INDEX: Supplier<String> = Supplier { "Axion Preview IB" }
    }
}

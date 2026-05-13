package axion.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.render.BuiltBuffer
import java.util.function.Supplier

class AxionPreviewBuffer : AutoCloseable {
    private var vertexBuffer: GpuBuffer? = null
    private var indexBuffer: GpuBuffer? = null
    private var indexCount: Int = 0
    private var indexType: VertexFormat.IndexType = VertexFormat.IndexType.SHORT
    private var vertexCount: Int = 0
    private var drawMode: VertexFormat.DrawMode = VertexFormat.DrawMode.TRIANGLES
    private var uploaded: Boolean = false

    val isUploaded: Boolean get() = uploaded
    val vertexBufferGpu: GpuBuffer? get() = vertexBuffer?.takeUnless { it.isClosed }
    val indexBufferGpu: GpuBuffer? get() = indexBuffer?.takeUnless { it.isClosed }
    val indexTypeValue: VertexFormat.IndexType get() = indexType
    val indexCountValue: Int get() = indexCount
    val vertexFormatValue: VertexFormat get() = RenderLayerCompat.blockTranslucentCull().vertexFormat
    val drawModeValue: VertexFormat.DrawMode get() = drawMode

    fun upload(builtBuffer: BuiltBuffer) {
        val params = builtBuffer.drawParameters
        val vertexData = builtBuffer.buffer
        val indexData = builtBuffer.sortedBuffer

        val device = RenderSystem.getDevice()
        val commandEncoder = device.createCommandEncoder()

        val existingVb = vertexBuffer
        if (existingVb == null || existingVb.isClosed || existingVb.size() < vertexData.remaining()) {
            vertexBuffer?.close()
            vertexBuffer = device.createBuffer(
                LABEL_VERTEX,
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_MAP_WRITE or GpuBuffer.USAGE_COPY_DST,
                vertexData,
            )
        } else if (!existingVb.isClosed) {
            commandEncoder.writeToBuffer(existingVb.slice(), vertexData)
        }

        if (indexData != null) {
            val existingIb = indexBuffer
            if (existingIb == null || existingIb.isClosed || existingIb.size() < indexData.remaining()) {
                indexBuffer?.close()
                indexBuffer = device.createBuffer(
                    LABEL_INDEX,
                    GpuBuffer.USAGE_INDEX or GpuBuffer.USAGE_MAP_WRITE or GpuBuffer.USAGE_COPY_DST,
                    indexData,
                )
            } else if (!existingIb.isClosed) {
                commandEncoder.writeToBuffer(existingIb.slice(), indexData)
            }
        } else {
            indexBuffer?.close()
            indexBuffer = null
        }

        vertexCount = params.vertexCount
        indexCount = params.indexCount
        drawMode = params.mode
        indexType = params.indexType
        uploaded = true
    }

    fun drawIndexed(renderPass: RenderPass) {
        val vb = vertexBuffer ?: return
        if (vb.isClosed) return

        renderPass.setVertexBuffer(0, vb)

        if (indexCount > 0) {
            val ib = indexBuffer
            if (ib != null && !ib.isClosed) {
                renderPass.setIndexBuffer(ib, indexType)
            } else {
                val sequential = RenderSystem.getSequentialBuffer(drawMode)
                renderPass.setIndexBuffer(
                    sequential.getIndexBuffer(indexCount),
                    sequential.indexType,
                )
            }
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

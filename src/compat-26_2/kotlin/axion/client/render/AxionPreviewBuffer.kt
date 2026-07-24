package axion.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.render.DrawMode
import java.util.function.Supplier

class AxionPreviewBuffer : AutoCloseable {
    private var vertexBuffer: GpuBuffer? = null
    private var indexBuffer: GpuBuffer? = null
    private var indexCount: Int = 0
    private var indexType: com.mojang.blaze3d.IndexType = com.mojang.blaze3d.IndexType.SHORT
    private var vertexCount: Int = 0
    private var drawMode: DrawMode = DrawMode.TRIANGLES
    private var vertexFormat: VertexFormat = RenderLayerCompat.blockTranslucentCull().format()
    private var uploaded: Boolean = false

    val isUploaded: Boolean get() = uploaded
    val vertexBufferGpu: GpuBuffer? get() = vertexBuffer?.takeUnless { it.isClosed }
    val indexBufferGpu: GpuBuffer? get() = indexBuffer?.takeUnless { it.isClosed }
    val indexTypeValue: com.mojang.blaze3d.IndexType get() = indexType
    val indexCountValue: Int get() = indexCount
    val vertexFormatValue: VertexFormat get() = vertexFormat
    val drawModeValue: DrawMode get() = drawMode

    fun upload(meshData: MeshData) {
        val params = meshData.drawState()
        val vertexData = meshData.vertexBuffer()
        val indexData = meshData.indexBuffer()

        val device = RenderSystem.getDevice()
        val commandEncoder = device.createCommandEncoder()

        val existingVb = vertexBuffer
        if (existingVb == null || existingVb.isClosed || existingVb.size() < vertexData.remaining().toLong()) {
            vertexBuffer?.close()
            vertexBuffer = device.createBuffer(
                LABEL_VERTEX,
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_MAP_WRITE or GpuBuffer.USAGE_COPY_DST,
                vertexData,
            )
        } else {
            commandEncoder.writeToBuffer(existingVb.slice(), vertexData)
        }

        if (indexData != null) {
            val existingIb = indexBuffer
            if (existingIb == null || existingIb.isClosed || existingIb.size() < indexData.remaining().toLong()) {
                indexBuffer?.close()
                indexBuffer = device.createBuffer(
                    LABEL_INDEX,
                    GpuBuffer.USAGE_INDEX or GpuBuffer.USAGE_MAP_WRITE or GpuBuffer.USAGE_COPY_DST,
                    indexData,
                )
            } else {
                commandEncoder.writeToBuffer(existingIb.slice(), indexData)
            }
        } else {
            indexBuffer?.close()
            indexBuffer = null
        }

        vertexFormat = params.format()
        vertexCount = params.vertexCount()
        indexCount = params.indexCount()
        drawMode = params.primitiveTopology()
        indexType = params.indexType()
        uploaded = true
    }

    fun drawIndexed(renderPass: RenderPass) {
        val vb = vertexBuffer ?: return
        if (vb.isClosed) return

        renderPass.setVertexBuffer(0, vb.slice())
        if (indexCount > 0) {
            val ib = indexBuffer
            if (ib != null && !ib.isClosed) {
                renderPass.setIndexBuffer(ib, indexType)
            } else {
                val sequential = RenderSystem.getSequentialBuffer(drawMode)
                renderPass.setIndexBuffer(sequential.getBuffer(indexCount), sequential.type())
            }
            // 26.2 reordered these, it did not just append firstInstance:
            // 26.1 was (baseVertex, firstIndex, indexCount, instanceCount);
            // 26.2 is (indexCount, instanceCount, firstIndex, baseVertex,
            // firstInstance). Passing the old argument order still compiles —
            // it just draws nothing — so the names are spelled out here.
            renderPass.drawIndexed(
                /* indexCount = */ indexCount,
                /* instanceCount = */ 1,
                /* firstIndex = */ 0,
                /* baseVertex = */ 0,
                /* firstInstance = */ 0,
            )
        } else if (vertexCount > 0) {
            // Likewise reordered: 26.1 was (firstVertex, vertexCount).
            renderPass.draw(
                /* vertexCount = */ vertexCount,
                /* instanceCount = */ 1,
                /* firstVertex = */ 0,
                /* firstInstance = */ 0,
            )
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

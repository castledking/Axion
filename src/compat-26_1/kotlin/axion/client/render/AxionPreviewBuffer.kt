package axion.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexSorting
import com.mojang.blaze3d.vertex.VertexFormat
import axion.client.render.gpu.PreviewTranslucencySortPolicy
import net.minecraft.client.render.DrawMode
import java.util.function.Supplier

class AxionPreviewBuffer : AutoCloseable {
    private var vertexBuffer: GpuBuffer? = null
    private var indexBuffer: GpuBuffer? = null
    private var indexCount: Int = 0
    private var indexType: VertexFormat.IndexType = VertexFormat.IndexType.SHORT
    private var vertexCount: Int = 0
    private var drawMode: DrawMode = DrawMode.TRIANGLES
    private var vertexFormat: VertexFormat = RenderLayerCompat.blockTranslucentCull().format()
    private var uploaded: Boolean = false
    private var quadSortState: MeshData.SortState? = null
    private var lastSortX: Float = Float.NaN
    private var lastSortY: Float = Float.NaN
    private var lastSortZ: Float = Float.NaN

    val isUploaded: Boolean get() = uploaded
    val vertexBufferGpu: GpuBuffer? get() = vertexBuffer?.takeUnless { it.isClosed }
    val indexBufferGpu: GpuBuffer? get() = indexBuffer?.takeUnless { it.isClosed }
    val indexTypeValue: VertexFormat.IndexType get() = indexType
    val indexCountValue: Int get() = indexCount
    val vertexFormatValue: VertexFormat get() = vertexFormat
    val drawModeValue: DrawMode get() = drawMode

    fun upload(
        meshData: MeshData,
        sortState: MeshData.SortState?,
        sortX: Float,
        sortY: Float,
        sortZ: Float,
    ) {
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
        drawMode = params.mode()
        indexType = params.indexType()
        quadSortState = sortState
        lastSortX = sortX
        lastSortY = sortY
        lastSortZ = sortZ
        uploaded = true
    }

    /** Rebuild only the translucent index order; vertex data stays resident. */
    fun resort(sortX: Float, sortY: Float, sortZ: Float) {
        val currentSortState = quadSortState ?: return
        if (!PreviewTranslucencySortPolicy.shouldResort(
                lastSortX,
                lastSortY,
                lastSortZ,
                sortX,
                sortY,
                sortZ,
            )
        ) return

        val indexBytes = indexCount.toLong() * indexType.bytes.toLong()
        require(indexBytes <= Int.MAX_VALUE) {
            "Preview index buffer exceeds the JVM native-buffer limit: $indexBytes bytes"
        }
        val allocator = ByteBufferBuilder(indexBytes.coerceAtLeast(1L).toInt())
        val sortedIndices = try {
            currentSortState.buildSortedIndexBuffer(
                allocator,
                VertexSorting.byDistance(sortX, sortY, sortZ),
            )
        } catch (t: Throwable) {
            allocator.close()
            throw t
        }
        if (sortedIndices == null) {
            allocator.close()
            return
        }
        try {
            val nextIndexBuffer = RenderSystem.getDevice().createBuffer(
                LABEL_INDEX,
                GpuBuffer.USAGE_INDEX or GpuBuffer.USAGE_MAP_WRITE or GpuBuffer.USAGE_COPY_DST,
                sortedIndices.byteBuffer(),
            )
            val previousIndexBuffer = indexBuffer
            indexBuffer = nextIndexBuffer
            previousIndexBuffer?.close()
            lastSortX = sortX
            lastSortY = sortY
            lastSortZ = sortZ
        } finally {
            sortedIndices.close()
            allocator.close()
        }
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
                renderPass.setIndexBuffer(sequential.getBuffer(indexCount), sequential.type())
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
        quadSortState = null
        lastSortX = Float.NaN
        lastSortY = Float.NaN
        lastSortZ = Float.NaN
    }

    companion object {
        private val LABEL_VERTEX: Supplier<String> = Supplier { "Axion Preview VB" }
        private val LABEL_INDEX: Supplier<String> = Supplier { "Axion Preview IB" }
    }
}

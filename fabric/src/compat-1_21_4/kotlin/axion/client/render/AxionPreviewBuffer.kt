package axion.client.render

import net.minecraft.client.gl.ShaderProgram
import net.minecraft.client.gl.VertexBuffer
import net.minecraft.client.render.BuiltBuffer
import org.joml.Matrix4f

/** Persistent OpenGL buffer used by the pre-1.21.5 preview renderer. */
class AxionPreviewBuffer : AutoCloseable {
    private var vertexBuffer: VertexBuffer? = null
    private var indexCount: Int = 0

    val isUploaded: Boolean
        get() = vertexBuffer?.let { !it.isClosed && indexCount > 0 } == true
    val indexCountValue: Int get() = indexCount

    /**
     * [VertexBuffer.upload] owns and closes [builtBuffer] on these Minecraft
     * versions, unlike the newer GpuBuffer upload path.
     */
    fun upload(builtBuffer: BuiltBuffer) {
        val drawParameters = builtBuffer.drawParameters
        val buffer = vertexBuffer?.takeUnless { it.isClosed }
            ?: createVertexBuffer().also { vertexBuffer = it }
        buffer.bind()
        try {
            buffer.upload(builtBuffer)
            indexCount = drawParameters.indexCount
        } finally {
            VertexBuffer.unbind()
        }
    }

    fun draw(modelView: Matrix4f, projection: Matrix4f, shader: ShaderProgram) {
        val buffer = vertexBuffer?.takeUnless { it.isClosed } ?: return
        if (indexCount <= 0) return
        buffer.bind()
        buffer.draw(modelView, projection, shader)
    }

    override fun close() {
        vertexBuffer?.close()
        vertexBuffer = null
        indexCount = 0
    }

    private fun createVertexBuffer(): VertexBuffer {
        val constructor = VertexBuffer::class.java.declaredConstructors.firstOrNull {
            it.parameterCount == 1 && it.parameterTypes.single().isEnum
        } ?: error("No compatible VertexBuffer usage constructor")
        constructor.isAccessible = true

        val usage = constructor.parameterTypes.single().enumConstants.firstOrNull { constant ->
            (constant as Enum<*>).name in setOf("DYNAMIC_WRITE", "DYNAMIC")
        } ?: error("No dynamic VertexBuffer usage constant")
        return constructor.newInstance(usage) as VertexBuffer
    }
}

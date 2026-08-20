package axion.client.render

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.rendertype.RenderType

/**
 * Cross-version compatibility extension for getting a buffer from consumers.
 * In 1.21.x (Yarn): Immediate has getBuffer(RenderType)
 * In 26.1.x (official): BufferSource implements MultiBufferSource which has getBuffer(RenderType)
 * Both types have the same method signature, so we use reflection to call it.
 */
fun Any.getBuffer(renderType: RenderType): VertexConsumer {
    val method = this.javaClass.methods.firstOrNull { 
        it.name == "getBuffer" && it.parameterCount == 1 && it.parameterTypes[0] == RenderType::class.java 
    } ?: error("getBuffer method not found on ${this.javaClass.name}")
    return method.invoke(this, renderType) as? VertexConsumer ?: error("getBuffer returned non-VertexConsumer")
}

/**
 * 26.2 deleted MultiBufferSource and hands renderers a SubmitNodeCollector, so
 * that range wraps it into something with getBuffer. 26.1 still gets a real
 * BufferSource from the render context, so this is identity.
 */
fun adaptConsumers(raw: Any): Any = raw

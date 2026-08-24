package axion.client.render

import net.minecraft.client.renderer.rendertype.RenderType
import com.mojang.blaze3d.addVertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource

fun Any.getBuffer(layer: RenderType): VertexConsumer =
    (this as MultiBufferSource).getBuffer(layer)

/**
 * 26.2 deleted MultiBufferSource and hands renderers a SubmitNodeCollector, so
 * that range wraps it into something with getBuffer. Every earlier range already
 * gets a usable buffer source from the render context, so this is identity.
 */
fun adaptConsumers(raw: Any): Any = raw

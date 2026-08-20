package axion.client.render

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider

fun Any.getBuffer(layer: RenderLayer): VertexConsumer =
    (this as VertexConsumerProvider).getBuffer(layer)

/**
 * 26.2 deleted MultiBufferSource and hands renderers a SubmitNodeCollector, so
 * that range wraps it into something with getBuffer. Every earlier range already
 * gets a usable buffer source from the render context, so this is identity.
 */
fun adaptConsumers(raw: Any): Any = raw

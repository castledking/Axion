package axion.client.render

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider

fun Any.getBuffer(layer: RenderLayer): VertexConsumer =
    (this as VertexConsumerProvider).getBuffer(layer)

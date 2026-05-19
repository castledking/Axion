package axion.client.render

import com.mojang.blaze3d.opengl.GlStateManager
import net.minecraft.client.render.RenderLayer

object DepthRenderCompat {
    fun renderThroughBlocks(
        consumers: Any,
        vararg layers: RenderLayer,
        render: () -> Unit,
    ) {
        GlStateManager._disableDepthTest()
        try {
            render()
            layers.forEach { flushLayer(consumers, it) }
        } finally {
            GlStateManager._enableDepthTest()
        }
    }

    private fun flushLayer(consumers: Any, layer: RenderLayer) {
        val methods = consumers.javaClass.methods.asSequence() + consumers.javaClass.declaredMethods.asSequence()
        val layerFlush = methods.firstOrNull { method ->
            (method.name == "draw" || method.name == "endBatch") &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isInstance(layer)
        }
        if (layerFlush != null) {
            layerFlush.isAccessible = true
            layerFlush.invoke(consumers, layer)
            return
        }

        val fullFlush = (consumers.javaClass.methods.asSequence() + consumers.javaClass.declaredMethods.asSequence())
            .firstOrNull { method ->
                (method.name == "draw" || method.name == "endBatch") && method.parameterCount == 0
            }
            ?: return
        fullFlush.isAccessible = true
        fullFlush.invoke(consumers)
    }
}

package axion.client.render

import net.minecraft.client.render.RenderLayer

object DepthRenderCompat {
    private val glStateManager: Class<*>? by lazy {
        runCatching { Class.forName("com.mojang.blaze3d.opengl.GlStateManager") }.getOrNull()
            ?: runCatching { Class.forName("com.mojang.blaze3d.platform.GlStateManager") }.getOrNull()
    }

    fun renderThroughBlocks(
        consumers: Any,
        vararg layers: RenderLayer,
        render: () -> Unit,
    ) {
        setDepthTest(enabled = false)
        try {
            render()
            layers.forEach { flushLayer(consumers, it) }
        } finally {
            setDepthTest(enabled = true)
        }
    }

    private fun setDepthTest(enabled: Boolean) {
        val manager = glStateManager ?: return
        val methodName = if (enabled) "_enableDepthTest" else "_disableDepthTest"
        runCatching {
            manager.getDeclaredMethod(methodName).invoke(null)
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

package axion.client.render

import net.fabricmc.fabric.api.event.Event
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import java.lang.reflect.Proxy
import org.slf4j.LoggerFactory

class AxionWorldRenderContext private constructor(
    private val delegate: Any?,
    private val fallbackConsumers: VertexConsumerProvider.Immediate?,
    private val fallbackMatrices: MatrixStack?,
) {
    constructor(delegate: Any) : this(delegate, null, null)

    constructor(consumers: VertexConsumerProvider.Immediate, matrices: MatrixStack) : this(null, consumers, matrices)

    fun consumers(): VertexConsumerProvider.Immediate {
        fallbackConsumers?.let { return it }
        val currentDelegate = delegate ?: error("World render delegate unavailable")
        return invokeNullable("consumers") as? VertexConsumerProvider.Immediate
            ?: error("World render consumers unavailable in ${currentDelegate.javaClass.name}")
    }

    fun matrices(): MatrixStack {
        fallbackMatrices?.let { return it }
        val currentDelegate = delegate ?: error("World render delegate unavailable")
        val value = invokeNullable("matrices") ?: invokeNullable("matrixStack")
        return value as? MatrixStack
            ?: error("Unsupported world render context: ${currentDelegate.javaClass.name}")
    }

    private fun invokeNullable(name: String): Any? {
        val currentDelegate = delegate ?: return null
        val method = currentDelegate.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            ?: return null
        return method.invoke(currentDelegate)
    }
}

object WorldRenderCompat {
    private val logger = LoggerFactory.getLogger(WorldRenderCompat::class.java)
    private val beforeDebugRenderCallbacks: MutableList<(AxionWorldRenderContext) -> Unit> = mutableListOf()
    private val endMainCallbacks: MutableList<(AxionWorldRenderContext) -> Unit> = mutableListOf()
    private val eventsClassNames: List<String> = listOf(
        "net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents",
        "net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents",
    )

    fun registerBeforeDebugRender(callback: (AxionWorldRenderContext) -> Unit) {
        register("BEFORE_DEBUG_RENDER", "DebugRender", callback, beforeDebugRenderCallbacks)
    }

    fun registerEndMain(callback: (AxionWorldRenderContext) -> Unit) {
        if (registerIfPresent("END_MAIN", "EndMain", callback)) {
            return
        }
        register("BEFORE_DEBUG_RENDER", "DebugRender", callback, endMainCallbacks)
    }

    fun dispatchFallbackCallbacks(
        consumers: VertexConsumerProvider.Immediate,
        matrices: MatrixStack,
    ) {
        if (!hasFallbackCallbacks()) {
            return
        }
        val client = MinecraftClient.getInstance()
        if (client.world == null || client.gameRenderer.camera == null) {
            return
        }
        val context = AxionWorldRenderContext(consumers, matrices)
        endMainCallbacks.forEach { it(context) }
        beforeDebugRenderCallbacks.forEach { it(context) }
        consumers.draw()
    }

    fun hasFallbackCallbacks(): Boolean {
        return beforeDebugRenderCallbacks.isNotEmpty() || endMainCallbacks.isNotEmpty()
    }

    private fun register(
        fieldName: String,
        nestedInterfaceName: String,
        callback: (AxionWorldRenderContext) -> Unit,
        fallbackCallbacks: MutableList<(AxionWorldRenderContext) -> Unit>,
    ) {
        if (!registerIfPresent(fieldName, nestedInterfaceName, callback)) {
            fallbackCallbacks += callback
            logger.warn(
                "Skipping Fabric world render callback registration for {} because the event API is unavailable in this runtime",
                fieldName,
            )
        }
    }

    private fun registerIfPresent(
        fieldName: String,
        nestedInterfaceName: String,
        callback: (AxionWorldRenderContext) -> Unit,
    ): Boolean {
        val eventsClass = eventsClass() ?: return false
        val eventField = runCatching { eventsClass.getField(fieldName) }.getOrNull() ?: return false
        val callbackType = runCatching {
            Class.forName("${eventsClass.name}$$nestedInterfaceName")
        }.getOrNull() ?: return false
        val listener = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { _, method, args ->
            when (method.name) {
                "equals" -> false
                "hashCode" -> System.identityHashCode(callback)
                "toString" -> "AxionWorldRenderCompat($fieldName)"
                else -> {
                    val rawContext = args?.firstOrNull() ?: return@newProxyInstance null
                    callback(AxionWorldRenderContext(rawContext))
                    null
                }
            }
        }
        val event = eventField.get(null) as? Event<Any>
            ?: error("Unexpected Fabric event type for $fieldName")
        event.register(listener)
        return true
    }

    private fun eventsClass(): Class<*>? {
        return eventsClassNames.firstNotNullOfOrNull { className ->
            runCatching { Class.forName(className) }.getOrNull()
        }
    }
}

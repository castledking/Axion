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

    fun drawConsumers() {
        try {
            consumers().draw()
        } catch (_: Throwable) {
        }
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
    private var fabricBeforeDebugRegistered = false
    private var fabricEndMainAvailable = false
    private val eventsClassNames: List<String> = listOf(
        "net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents",
        "net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents",
    )

    fun registerBeforeDebugRender(callback: (AxionWorldRenderContext) -> Unit) {
        beforeDebugRenderCallbacks += callback
        ensureFabricBeforeDebugListener()
    }

    fun registerEndMain(callback: (AxionWorldRenderContext) -> Unit) {
        endMainCallbacks += callback
        if (!tryRegisterEndMainListener()) {
            logger.warn(
                "Fabric END_MAIN event unavailable — using fallback mixin path",
            )
        }
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
        val needsBeforeDebugFallback = beforeDebugRenderCallbacks.isNotEmpty() && !fabricBeforeDebugRegistered
        val needsEndMainFallback = endMainCallbacks.isNotEmpty() && !fabricEndMainAvailable
        return needsBeforeDebugFallback || needsEndMainFallback
    }

    private fun ensureFabricBeforeDebugListener() {
        if (fabricBeforeDebugRegistered) return
        val registered = registerBatchedListener("BEFORE_DEBUG_RENDER", "DebugRender") { rawContext ->
            val ctx = AxionWorldRenderContext(rawContext)
            if (!fabricEndMainAvailable) {
                for (cb in endMainCallbacks) cb(ctx)
            }
            for (cb in beforeDebugRenderCallbacks) cb(ctx)
            ctx.drawConsumers()
        }
        if (registered) {
            fabricBeforeDebugRegistered = true
        } else {
            logger.warn(
                "Fabric BEFORE_DEBUG_RENDER event unavailable — using fallback mixin path",
            )
        }
    }

    private fun tryRegisterEndMainListener(): Boolean {
        if (fabricEndMainAvailable) return true
        val registered = registerBatchedListener("END_MAIN", "EndMain") { rawContext ->
            val ctx = AxionWorldRenderContext(rawContext)
            for (cb in endMainCallbacks) cb(ctx)
            ctx.drawConsumers()
        }
        if (registered) {
            fabricEndMainAvailable = true
        }
        return registered
    }

    private fun registerBatchedListener(
        fieldName: String,
        nestedInterfaceName: String,
        dispatch: (Any) -> Unit,
    ): Boolean {
        val eventsClass = eventsClass() ?: return false
        val eventField = runCatching { eventsClass.getField(fieldName) }.getOrNull() ?: return false
        val callbackType = runCatching {
            Class.forName("${eventsClass.name}$$nestedInterfaceName")
        }.getOrNull() ?: return false
        val listener = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { _, method, args ->
            when (method.name) {
                "equals" -> false
                "hashCode" -> System.identityHashCode(dispatch)
                "toString" -> "AxionWorldRenderCompat($fieldName)"
                else -> {
                    val rawContext = args?.firstOrNull() ?: return@newProxyInstance null
                    dispatch(rawContext)
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

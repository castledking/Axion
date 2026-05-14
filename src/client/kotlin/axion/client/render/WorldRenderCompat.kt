package axion.client.render

import axion.client.render.gpu.ChunkedPreviewLifecycle
import net.fabricmc.fabric.api.event.Event
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.Immediate
import net.minecraft.client.util.math.MatrixStack
import java.lang.reflect.Proxy
import org.slf4j.LoggerFactory

class AxionWorldRenderContext private constructor(
    private val delegate: Any?,
    private val fallbackConsumers: Immediate?,
    private val fallbackMatrices: MatrixStack?,
) {
    constructor(delegate: Any) : this(delegate, null, null)

    constructor(consumers: Immediate, matrices: MatrixStack) : this(null, consumers, matrices)

    fun consumers(): Any {
        fallbackConsumers?.let { return it }
        val currentDelegate = delegate ?: error("World render delegate unavailable")
        // Try both old (consumers) and new (bufferSource) method names for cross-version compatibility
        return invokeNullable("consumers") ?: invokeNullable("bufferSource")
            ?: error("World render consumers unavailable in ${currentDelegate.javaClass.name}")
    }

    fun matrices(): MatrixStack {
        fallbackMatrices?.let { return it }
        val currentDelegate = delegate ?: error("World render delegate unavailable")
        // Try both old (matrices/matrixStack) and new (poseStack) method names for cross-version compatibility
        val value = invokeNullable("matrices") ?: invokeNullable("matrixStack") ?: invokeNullable("poseStack")
            ?: error("World render matrices unavailable - tried matrices, matrixStack, poseStack on ${currentDelegate.javaClass.name}")
        return value as? MatrixStack
            ?: error("World render matrices type mismatch: got ${value.javaClass.name}, expected MatrixStack")
    }

    /**
     * All MC versions use camera-relative coordinates. Returning false makes all
     * rendering invisible, so this always returns true.
     * The outline-vs-filled offset on 26.1 has a different cause (may be a
     * VertexPipeline difference between addVertex(Pose) and addVertex(Matrix4f)
     * in the new GPU pipeline).
     */
    fun needsCameraOffset(): Boolean {
        val currentDelegate = delegate ?: return true
        return true
    }

    fun drawConsumers() {
        try {
            val consumers = consumers()
            invokeNullable(consumers, "draw")
                ?: invokeNullable(consumers, "endBatch")
        } catch (_: Throwable) {
        }
    }

    private fun invokeNullable(obj: Any, name: String): Any? {
        val method = obj.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            ?: return null
        return method.invoke(obj)
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
        "net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents",
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
            ensureFabricBeforeDebugListener()
        }
    }

    private var loggedFallbackDispatch = false

    fun dispatchFallbackCallbacks(
        consumers: Immediate,
        matrices: MatrixStack,
    ) {
        if (!hasFallbackCallbacks()) {
            return
        }
        val client = MinecraftClient.getInstance()
        if (client.world == null || client.gameRenderer.camera == null) {
            return
        }
        if (!loggedFallbackDispatch) {
            loggedFallbackDispatch = true
            logger.info(
                "[Axion/Render] Mixin fallback dispatching {} endMain + {} beforeDebug callbacks",
                endMainCallbacks.size,
                beforeDebugRenderCallbacks.size,
            )
        }
        val context = AxionWorldRenderContext(consumers, matrices)
        endMainCallbacks.forEach { it(context) }
        beforeDebugRenderCallbacks.forEach { it(context) }
        // Flush deferred draws with no parameters - use internal defaults
        ChunkedPreviewLifecycle.flushDeferredDraws()
        consumers.draw()
    }

    fun hasFallbackCallbacks(): Boolean {
        val needsBeforeDebugFallback = beforeDebugRenderCallbacks.isNotEmpty() && !fabricBeforeDebugRegistered
        val needsEndMainFallback = endMainCallbacks.isNotEmpty() && !fabricEndMainAvailable
        return needsBeforeDebugFallback || needsEndMainFallback
    }

    private fun ensureFabricBeforeDebugListener() {
        if (fabricBeforeDebugRegistered) return
        val fieldNames = listOf("BEFORE_GIZMOS", "BEFORE_DEBUG_RENDER", "BEFORE_BLOCK_OUTLINE", "END_EXTRACTION")
        val nestedInterfaceNames = listOf("BeforeGizmos", "DebugRender", "BeforeBlockOutline", "EndExtraction")
        for (i in fieldNames.indices) {
            val registered = registerBatchedListener(fieldNames[i], nestedInterfaceNames[i]) { rawContext ->
                val ctx = AxionWorldRenderContext(rawContext)
                if (!fabricEndMainAvailable) {
                    for (cb in endMainCallbacks) cb(ctx)
                }
                for (cb in beforeDebugRenderCallbacks) cb(ctx)
                // Flush deferred draws with no parameters - use internal defaults
                ChunkedPreviewLifecycle.flushDeferredDraws()
                ctx.drawConsumers()
            }
            if (registered) {
                fabricBeforeDebugRegistered = true
                return
            }
        }
        logger.warn("[Axion/Render] No Fabric render event available — using mixin fallback path")
    }

    private fun tryRegisterEndMainListener(): Boolean {
        if (fabricEndMainAvailable) return true
        val registered = registerBatchedListener("END_MAIN", "EndMain") { rawContext ->
            val ctx = AxionWorldRenderContext(rawContext)
            try {
                for (cb in endMainCallbacks) cb(ctx)
                // Flush deferred draws with no parameters - use internal defaults
                ChunkedPreviewLifecycle.flushDeferredDraws()
                ctx.drawConsumers()
            } catch (e: Throwable) {
                logger.error("[Axion/Render] END_MAIN dispatch error", e)
            }
        }
        if (registered) {
            fabricEndMainAvailable = true
        }
        return registered
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerBatchedListener(
        fieldName: String,
        nestedInterfaceName: String,
        dispatch: (Any) -> Unit,
    ): Boolean {
        val eventsClass = eventsClass() ?: return false
        val eventField = runCatching { eventsClass.getField(fieldName) }.getOrNull() ?: return false
        val fqCallbackName = "${eventsClass.name}\$$nestedInterfaceName"
        val callbackType = runCatching { Class.forName(fqCallbackName) }.getOrNull() ?: return false
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
        logger.info("[Axion/Render] Registered via {}.{}", eventsClass.simpleName, fieldName)
        return true
    }

    private fun eventsClass(): Class<*>? {
        return eventsClassNames.firstNotNullOfOrNull { className ->
            runCatching { Class.forName(className) }.getOrNull()
        }
    }
}

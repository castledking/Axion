package axion.client.render

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
        logger.info("[Axion/Render] registerEndMain called, {} callbacks total", endMainCallbacks.size)
        if (!tryRegisterEndMainListener()) {
            logger.warn(
                "[Axion/Render] Fabric END_MAIN event unavailable — using fallback mixin path",
            )
        } else {
            logger.info("[Axion/Render] END_MAIN event registered successfully")
        }
    }

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
        // Try multiple field names for cross-version compatibility
        val fieldNames = listOf("BEFORE_GIZMOS", "BEFORE_DEBUG_RENDER", "BEFORE_BLOCK_OUTLINE", "END_EXTRACTION")
        val nestedInterfaceNames = listOf("BeforeGizmos", "DebugRender", "BeforeBlockOutline", "EndExtraction")
        var registered = false
        for (i in fieldNames.indices) {
            registered = registerBatchedListener(fieldNames[i], nestedInterfaceNames[i]) { rawContext ->
                val ctx = AxionWorldRenderContext(rawContext)
                if (!fabricEndMainAvailable) {
                    for (cb in endMainCallbacks) cb(ctx)
                }
                for (cb in beforeDebugRenderCallbacks) cb(ctx)
                ctx.drawConsumers()
            }
            if (registered) {
                logger.info("Fabric rendering event registered: ${fieldNames[i]}")
                break
            }
        }
        if (registered) {
            fabricBeforeDebugRegistered = true
        } else {
            logger.warn(
                "Fabric before-debug-render event unavailable — using fallback mixin path",
            )
        }
    }

    private fun tryRegisterEndMainListener(): Boolean {
        if (fabricEndMainAvailable) return true
        val registered = registerBatchedListener("END_MAIN", "EndMain") { rawContext ->
            val ctx = AxionWorldRenderContext(rawContext)
            try {
                for (cb in endMainCallbacks) cb(ctx)
                ctx.drawConsumers()
            } catch (e: Throwable) {
                logger.error("[Axion/Render] END_MAIN dispatch error", e)
            }
        }
        if (registered) {
            fabricEndMainAvailable = true
            logger.info("[Axion/Render] tryRegisterEndMainListener succeeded")
        } else {
            logger.warn("[Axion/Render] tryRegisterEndMainListener FAILED")
        }
        return registered
    }

    private fun registerBatchedListener(
        fieldName: String,
        nestedInterfaceName: String,
        dispatch: (Any) -> Unit,
    ): Boolean {
        val eventsClass = eventsClass()
        if (eventsClass == null) {
            logger.warn("[Axion/Render] registerBatchedListener($fieldName): eventsClass() returned null")
            return false
        }
        logger.info("[Axion/Render] registerBatchedListener($fieldName): eventsClass={}", eventsClass.name)
        val eventField = runCatching { eventsClass.getField(fieldName) }.getOrNull()
        if (eventField == null) {
            logger.warn("[Axion/Render] registerBatchedListener($fieldName): field not found in {}", eventsClass.name)
            return false
        }
        val fqCallbackName = "${eventsClass.name}\$$nestedInterfaceName"
        val callbackType = runCatching {
            Class.forName(fqCallbackName)
        }.getOrNull()
        if (callbackType == null) {
            logger.warn("[Axion/Render] registerBatchedListener($fieldName): callback class not found: {}", fqCallbackName)
            return false
        }
        logger.info("[Axion/Render] registerBatchedListener($fieldName): callbackType={}", callbackType.name)
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
        logger.info("[Axion/Render] registerBatchedListener($fieldName): registered successfully")
        return true
    }

    private fun eventsClass(): Class<*>? {
        return eventsClassNames.firstNotNullOfOrNull { className ->
            runCatching { Class.forName(className) }.getOrNull()
        }
    }
}

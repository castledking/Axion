package axion.client.render

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import com.mojang.blaze3d.vertex.PoseStack
import org.slf4j.LoggerFactory

class AxionWorldRenderContext private constructor(
    private val delegate: Any?,
    private val fallbackConsumers: MultiBufferSource.BufferSource?,
    private val fallbackMatrices: PoseStack?,
) {
    constructor(delegate: Any) : this(delegate, null, null)

    constructor(consumers: MultiBufferSource.BufferSource, matrices: PoseStack) : this(null, consumers, matrices)

    // Every renderer calls consumers() separately, and on 26.2 the adapter owns
    // the per-render-type batches that are flushed at the end of the frame.
    // Adapting once per context is what keeps those batches from being dropped.
    private var adaptedConsumers: Any? = null

    fun consumers(): Any {
        fallbackConsumers?.let { return it }
        adaptedConsumers?.let { return it }
        val currentDelegate = delegate ?: error("World render delegate unavailable")
        // consumers/bufferSource cover 1.21.x through 26.1. 26.2 deleted
        // MultiBufferSource and exposes a SubmitNodeCollector instead, which
        // adaptConsumers wraps into something with getBuffer.
        val raw = invokeNullable("consumers")
            ?: invokeNullable("bufferSource")
            ?: invokeNullable("submitNodeCollector")
            ?: error("World render consumers unavailable in ${currentDelegate.javaClass.name}")
        return adaptConsumers(raw).also { adaptedConsumers = it }
    }

    fun matrices(): PoseStack {
        fallbackMatrices?.let { return it }
        val currentDelegate = delegate ?: error("World render delegate unavailable")
        // Try both old (matrices/matrixStack) and new (poseStack) method names for cross-version compatibility
        val value = invokeNullable("matrices") ?: invokeNullable("matrixStack") ?: invokeNullable("poseStack")
        ?: error("World render matrices unavailable - tried matrices, matrixStack, poseStack on ${currentDelegate.javaClass.name}")
        return value as? PoseStack
            ?: error("World render matrices type mismatch: got ${value.javaClass.name}, expected PoseStack")
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

    private var loggedDrawConsumersError = false

    fun drawConsumers() {
        try {
            val consumers = consumers()
            invokeNullable(consumers, "draw")
                ?: invokeNullable(consumers, "endBatch")
        } catch (t: Throwable) {
            if (!loggedDrawConsumersError) {
                loggedDrawConsumersError = true
                LoggerFactory.getLogger(AxionWorldRenderContext::class.java)
                    .warn("[Axion/Render] drawConsumers failed; suppressing further errors", t)
            }
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

    private fun flushDeferredDraws() {
        // Try to flush GPU preview draws if available (1.21.4+), otherwise no-op (1.21.0-1.21.3)
        try {
            val lifecycleClass = Class.forName("axion.client.render.gpu.ChunkedPreviewLifecycle")
            val flushMethod = lifecycleClass.getMethod("flushDeferredDraws")
            flushMethod.invoke(null)
        } catch (e: Exception) {
            // Class doesn't exist in this version, no-op
        }
    }

    fun registerBeforeDebugRender(callback: (AxionWorldRenderContext) -> Unit) {
        beforeDebugRenderCallbacks += callback
    }

    fun registerEndMain(callback: (AxionWorldRenderContext) -> Unit) {
        endMainCallbacks += callback
    }

    private var loggedFallbackDispatch = false

    @Suppress("SENSELESS_COMPARISON") // Camera became non-null in 26.1; check is still required on 1.21.x.
    fun dispatchFallbackCallbacks(
        consumers: MultiBufferSource.BufferSource,
        matrices: PoseStack,
    ) {
        if (!hasFallbackCallbacks()) {
            return
        }
        val client = Minecraft.getInstance()
        if (client.level == null || client.gameRenderer.mainCamera == null) {
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
        flushDeferredDraws()
        consumers.endBatch()
    }

    fun hasFallbackCallbacks(): Boolean {
        return beforeDebugRenderCallbacks.isNotEmpty() || endMainCallbacks.isNotEmpty()
    }
}

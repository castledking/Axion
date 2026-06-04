package axion.client.render

import axion.client.compat.VersionCompatImpl
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object ShaderPackCompat {
    private val logger = LoggerFactory.getLogger(ShaderPackCompat::class.java)
    private var loggedGpuFallback = false
    private var lastShaderPackActive: Boolean? = null

    /**
     * Returns true when an Iris shader pack is active. Direct GPU preview draws
     * (separate render pass on the main framebuffer) do not composite correctly
     * with Iris, so callers should route through the legacy consumer path instead.
     */
    fun shouldDisableDirectGpuPreview(): Boolean {
        val shaderPackActive = FabricLoader.getInstance().isModLoaded("iris") && isIrisShaderPackActive()
        observeShaderPackState(shaderPackActive)
        if (shaderPackActive && !loggedGpuFallback) {
            loggedGpuFallback = true
            logger.info("[Axion GPU] Active Iris shader pack detected; using CPU preview renderer for shader compatibility")
        }
        return shaderPackActive
    }

    private fun observeShaderPackState(shaderPackActive: Boolean) {
        val previous = lastShaderPackActive
        lastShaderPackActive = shaderPackActive
        if (previous == null || previous == shaderPackActive) return

        if (!shaderPackActive) {
            loggedGpuFallback = false
        }
        logger.info(
            "[Axion GPU] Iris shader-pack state changed: active={}; resetting cached GPU preview state",
            shaderPackActive,
        )
        resetDirectGpuPreviewState()
    }

    private fun resetDirectGpuPreviewState() {
        runCatching {
            VersionCompatImpl.closeChunkedPreviews()
        }.onFailure {
            logger.debug("[Axion GPU] Failed to close chunked preview sessions after shader-pack state change", it)
        }

        runCatching {
            val drawerClass = Class.forName("axion.client.render.gpu.AxionPreviewBlockDrawer")
            val instance = drawerClass.getField("INSTANCE").get(null)
            drawerClass.getMethod("resetFailureState").invoke(instance)
        }.onFailure {
            logger.debug("[Axion GPU] Failed to reset GPU preview drawer after shader-pack state change", it)
        }
    }

    private fun isIrisShaderPackActive(): Boolean {
        val apiResult = runCatching {
            val apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
            val instance = apiClass.getMethod("getInstance").invoke(null)
            apiClass.getMethod("isShaderPackInUse").invoke(instance) as? Boolean
        }.getOrNull()
        if (apiResult != null) {
            return apiResult
        }

        return runCatching {
            val irisClass = Class.forName("net.irisshaders.iris.Iris")
            irisClass.getMethod("isPackInUseQuick").invoke(null) as? Boolean
                ?: (irisClass.getMethod("getCurrentPack").invoke(null) as? java.util.Optional<*>)?.isPresent
                ?: false
        }.getOrDefault(false)
    }
}

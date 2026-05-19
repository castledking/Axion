package axion.client.render

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object ShaderPackCompat {
    private val logger = LoggerFactory.getLogger(ShaderPackCompat::class.java)
    private var loggedGpuFallback = false

    fun shouldDisableDirectGpuPreview(): Boolean {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return false
        }
        val shaderPackActive = isIrisShaderPackActive()
        if (shaderPackActive && !loggedGpuFallback) {
            loggedGpuFallback = true
            logger.info("[Axion GPU] Active Iris shader pack detected; using CPU preview renderer for shader compatibility")
        }
        return shaderPackActive
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

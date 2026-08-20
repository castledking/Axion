package axion.client.compat

import net.fabricmc.loader.api.FabricLoader

object CrowBarCompat {
    private const val OWNER = "axion"

    private data class SuppressMethod(
        val method: java.lang.reflect.Method,
        val supportsVanillaLocatorFlag: Boolean,
    )

    private val suppressLocatorBarMethod: SuppressMethod? by lazy {
        if (!FabricLoader.getInstance().isModLoaded("crowbar")) {
            return@lazy null
        }
        runCatching {
            val eventsClass = Class.forName("codes.castled.crowbar.api.CrowBarRenderEvents")
            runCatching {
                SuppressMethod(
                    eventsClass.getMethod(
                        "setLocatorBarSuppressed",
                        String::class.java,
                        Boolean::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                    ),
                    supportsVanillaLocatorFlag = true,
                )
            }.getOrElse {
                SuppressMethod(
                    eventsClass.getMethod(
                        "setLocatorBarSuppressed",
                        String::class.java,
                        Boolean::class.javaPrimitiveType,
                    ),
                    supportsVanillaLocatorFlag = false,
                )
            }
        }.recoverCatching {
            SuppressMethod(
                Class.forName("codes.castled.crowbar.CrowBarState").getMethod(
                    "setExternalRenderSuppressed",
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ),
                supportsVanillaLocatorFlag = true,
            )
        }.getOrNull()
    }

    fun setLocatorBarSuppressed(suppressed: Boolean, keepVanillaLocatorBar: Boolean = true) {
        val suppression = suppressLocatorBarMethod ?: return
        runCatching {
            if (suppression.supportsVanillaLocatorFlag) {
                suppression.method.invoke(null, OWNER, suppressed, keepVanillaLocatorBar)
            } else {
                suppression.method.invoke(null, OWNER, suppressed)
            }
        }
    }
}

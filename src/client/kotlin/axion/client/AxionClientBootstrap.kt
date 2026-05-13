package axion.client

import axion.client.compat.VersionCompatInit
import axion.client.compat.VersionCompatImpl
import axion.client.config.AxionClientConfig
import axion.client.hotbar.AxionHotbarHud
import axion.client.hotbar.AxionToolHintHud
import axion.client.input.AxionKeybindings
import axion.client.network.AxionServerConnection
import axion.client.input.AxionTickHandler
import axion.common.compat.VersionCompat
import axion.client.render.WorldRenderCompat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException

object AxionClientBootstrap {
    private val logger = LoggerFactory.getLogger(AxionClientBootstrap::class.java)
    private val hudId: Identifier by lazy { VersionCompat.INSTANCE.identifierOf("axion", "side_hotbar") }
    private val hintHudId: Identifier by lazy { VersionCompat.INSTANCE.identifierOf("axion", "tool_hints") }
    private val failedRenderers = linkedSetOf<String>()

    fun initialize() {
        VersionCompatInit.init()
        AxionClientConfig.initialize()
        AxionServerConnection.initialize()
        AxionKeybindings.register()
        VersionCompatImpl.registerHudElements(hudId, hintHudId, AxionHotbarHud::render, AxionToolHintHud::render)
        ClientTickEvents.END_CLIENT_TICK.register(AxionTickHandler::onEndTick)

        // Register renderers directly
        registerRenderers()
    }

    private fun registerRenderers() {
        try {
            val rendererClasses = listOf(
                "axion.client.render.PlacementPreviewRenderer",
                "axion.client.render.ExtrudePreviewRenderer",
                "axion.client.render.SmearPreviewRenderer",
                "axion.client.render.StackPreviewRenderer",
                "axion.client.render.SymmetryGizmoRenderer",
                "axion.client.render.TargetHighlightRenderer",
                "axion.client.render.SelectionStateRenderer",
            )

            val contextClass = Class.forName("axion.client.render.AxionWorldRenderContext")

            for (rendererClassName in rendererClasses) {
                try {
                    val rendererClass = Class.forName(rendererClassName)
                    val renderMethod = rendererClass.getMethod("render", contextClass)
                    val rendererInstance = rendererClass.getDeclaredField("INSTANCE").get(null)

                    WorldRenderCompat.registerEndMain { context ->
                        try {
                            renderMethod.invoke(rendererInstance, context)
                        } catch (e: Exception) {
                            val cause = (e as? InvocationTargetException)?.targetException ?: e
                            if (failedRenderers.add(rendererClassName)) {
                                logger.warn(
                                    "[Axion/Bootstrap] Renderer {} failed; suppressing repeated render errors",
                                    rendererClassName,
                                    cause,
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("[Axion/Bootstrap] Failed to register renderer {}: {}", rendererClassName, e.message)
                }
            }
        } catch (e: Exception) {
            logger.error("[Axion/Bootstrap] Failed to register renderers", e)
        }
    }
}

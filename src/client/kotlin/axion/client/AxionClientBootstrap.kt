package axion.client

import axion.client.compat.VersionCompatInit
import axion.client.compat.VersionCompatImpl
import axion.client.config.AxionClientConfig
import axion.client.hotbar.AxionHotbarHud
import axion.client.hotbar.AxionToolHintHud
import axion.client.input.AxionKeybindings
import axion.client.network.AxionServerConnection
import axion.client.input.AxionTickHandler
import axion.client.render.ExtrudePreviewRenderer
import axion.client.render.SelectionBoxRenderer
import axion.client.render.SymmetryGizmoRenderer
import axion.client.render.SymmetryPreviewRenderer
import axion.client.render.TargetHighlightRenderer
import axion.client.render.PlacementPreviewRenderer
import axion.client.render.SmearPreviewRenderer
import axion.client.render.StackPreviewRenderer
import axion.client.render.WorldRenderCompat
import axion.common.compat.VersionCompat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.util.Identifier

object AxionClientBootstrap {
    private val hudId: Identifier by lazy { VersionCompat.INSTANCE.identifierOf("axion", "side_hotbar") }
    private val hintHudId: Identifier by lazy { VersionCompat.INSTANCE.identifierOf("axion", "tool_hints") }

    fun initialize() {
        VersionCompatInit.init()
        AxionClientConfig.initialize()
        AxionServerConnection.initialize()
        AxionKeybindings.register()
        VersionCompatImpl.registerHudElements(hudId, hintHudId, AxionHotbarHud::render, AxionToolHintHud::render)
        ClientTickEvents.END_CLIENT_TICK.register(AxionTickHandler::onEndTick)
        WorldRenderCompat.registerEndMain(TargetHighlightRenderer::render)
        WorldRenderCompat.registerEndMain(SelectionBoxRenderer::render)
        WorldRenderCompat.registerEndMain(PlacementPreviewRenderer::render)
        WorldRenderCompat.registerEndMain(StackPreviewRenderer::render)
        WorldRenderCompat.registerEndMain(SmearPreviewRenderer::render)
        WorldRenderCompat.registerEndMain(ExtrudePreviewRenderer::render)
        WorldRenderCompat.registerEndMain(SymmetryGizmoRenderer::render)
        WorldRenderCompat.registerEndMain(SymmetryPreviewRenderer::render)
    }
}

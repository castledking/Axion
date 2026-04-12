package axion.client

import axion.client.compat.VersionCompatInit
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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.util.Identifier

object AxionClientBootstrap {
    private val hudId: Identifier = Identifier.of("axion", "side_hotbar")
    private val hintHudId: Identifier = Identifier.of("axion", "tool_hints")

    fun initialize() {
        VersionCompatInit.init()
        AxionClientConfig.initialize()
        AxionServerConnection.initialize()
        AxionKeybindings.register()
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, hudId, AxionHotbarHud::render)
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, hintHudId, AxionToolHintHud::render)
        ClientTickEvents.END_CLIENT_TICK.register(AxionTickHandler::onEndTick)
        WorldRenderCompat.registerBeforeDebugRender(TargetHighlightRenderer::render)
        WorldRenderCompat.registerEndMain(SelectionBoxRenderer::render)
        WorldRenderCompat.registerBeforeDebugRender(PlacementPreviewRenderer::render)
        WorldRenderCompat.registerBeforeDebugRender(StackPreviewRenderer::render)
        WorldRenderCompat.registerBeforeDebugRender(SmearPreviewRenderer::render)
        WorldRenderCompat.registerBeforeDebugRender(ExtrudePreviewRenderer::render)
        WorldRenderCompat.registerBeforeDebugRender(SymmetryGizmoRenderer::render)
        WorldRenderCompat.registerBeforeDebugRender(SymmetryPreviewRenderer::render)
    }
}

package axion.client

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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.util.Identifier

object AxionClientBootstrap {
    private val hudId: Identifier = Identifier.of("axion", "side_hotbar")
    private val hintHudId: Identifier = Identifier.of("axion", "tool_hints")

    fun initialize() {
        AxionServerConnection.initialize()
        AxionKeybindings.register()
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, hudId, AxionHotbarHud::render)
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, hintHudId, AxionToolHintHud::render)
        ClientTickEvents.END_CLIENT_TICK.register(AxionTickHandler::onEndTick)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TargetHighlightRenderer::render)
        WorldRenderEvents.END_MAIN.register(SelectionBoxRenderer::render)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(PlacementPreviewRenderer::render)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(StackPreviewRenderer::render)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(SmearPreviewRenderer::render)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ExtrudePreviewRenderer::render)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(SymmetryGizmoRenderer::render)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(SymmetryPreviewRenderer::render)
    }
}

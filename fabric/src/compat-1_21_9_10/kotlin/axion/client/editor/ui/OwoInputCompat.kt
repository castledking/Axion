package axion.client.editor.ui

import io.wispforest.owo.ui.core.OwoUIAdapter
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Click
import net.minecraft.client.input.MouseInput

/**
 * owo 0.12.24 for 1.21.9-1.21.10: mouse events are already wrapped in vanilla's
 * click record (1.21.9 introduced Click), exactly as on 0.13.x, but the component
 * type still carries its 0.12 name, Component, rather than UIComponent. The raw
 * (mouseX, mouseY, button) form this file used before matched no owo release for
 * these versions and never compiled.
 */
internal fun owoRender(
    adapter: OwoUIAdapter<*>,
    context: DrawContext,
    mouseX: Int,
    mouseY: Int,
    partialTick: Float,
) {
    adapter.render(context, mouseX, mouseY, partialTick)
}

internal fun owoMouseClick(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    button: Int,
): Boolean = adapter.mouseClicked(Click(mouseX, mouseY, MouseInput(button, 0)), false)

internal fun owoMouseRelease(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    button: Int,
): Boolean = adapter.mouseReleased(Click(mouseX, mouseY, MouseInput(button, 0)))

internal fun owoMouseDrag(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    button: Int,
    deltaX: Double,
    deltaY: Double,
): Boolean = adapter.mouseDragged(Click(mouseX, mouseY, MouseInput(button, 0)), deltaX, deltaY)

internal fun owoMouseScroll(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    horizontal: Double,
    vertical: Double,
): Boolean = adapter.mouseScrolled(mouseX, mouseY, horizontal, vertical)

internal fun owoSubscribeMouseDown(
    component: io.wispforest.owo.ui.core.Component,
    handler: (button: Int) -> Boolean,
) {
    component.mouseDown().subscribe { click, _ -> handler(click.button()) }
}

internal fun owoPartialTick(tickCounter: net.minecraft.client.render.RenderTickCounter): Float =
    tickCounter.getTickProgress(false)

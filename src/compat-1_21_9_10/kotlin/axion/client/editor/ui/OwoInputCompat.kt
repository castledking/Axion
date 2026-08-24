package axion.client.editor.ui

import io.wispforest.owo.ui.core.OwoUIAdapter
import net.minecraft.client.gui.DrawContext

/**
 * Legacy owo generation (0.12.x): mouse events arrive as raw
 * (mouseX, mouseY, button) triples and rendering takes a plain partial tick.
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
): Boolean = adapter.mouseClicked(mouseX, mouseY, button)

internal fun owoMouseRelease(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    button: Int,
): Boolean = adapter.mouseReleased(mouseX, mouseY, button)

internal fun owoMouseDrag(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    button: Int,
    deltaX: Double,
    deltaY: Double,
): Boolean = adapter.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)

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
    component.mouseDown().subscribe { _, _, button -> handler(button) }
}

internal fun owoPartialTick(tickCounter: net.minecraft.client.render.RenderTickCounter): Float =
    tickCounter.getTickProgress(false)

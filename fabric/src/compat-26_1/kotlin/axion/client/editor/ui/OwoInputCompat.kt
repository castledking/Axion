package axion.client.editor.ui

import io.wispforest.owo.ui.core.OwoUIAdapter
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo

/**
 * Modern owo generation (0.13.x): mouse events are wrapped in vanilla's
 * MouseButtonEvent record.
 */
internal fun owoRender(
    adapter: OwoUIAdapter<*>,
    context: DrawContext,
    mouseX: Int,
    mouseY: Int,
    partialTick: Float,
) {
    adapter.extractRenderState(context.delegate, mouseX, mouseY, partialTick)
}

internal fun owoMouseClick(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    button: Int,
): Boolean = adapter.mouseClicked(MouseButtonEvent(mouseX, mouseY, MouseButtonInfo(button, 0)), false)

internal fun owoMouseRelease(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    button: Int,
): Boolean = adapter.mouseReleased(MouseButtonEvent(mouseX, mouseY, MouseButtonInfo(button, 0)))

internal fun owoMouseDrag(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    button: Int,
    deltaX: Double,
    deltaY: Double,
): Boolean = adapter.mouseDragged(MouseButtonEvent(mouseX, mouseY, MouseButtonInfo(button, 0)), deltaX, deltaY)

internal fun owoMouseScroll(
    adapter: OwoUIAdapter<*>,
    mouseX: Double,
    mouseY: Double,
    horizontal: Double,
    vertical: Double,
): Boolean = adapter.mouseScrolled(mouseX, mouseY, horizontal, vertical)

internal fun owoSubscribeMouseDown(
    component: io.wispforest.owo.ui.core.UIComponent,
    handler: (button: Int) -> Boolean,
) {
    component.mouseDown().subscribe { click, _ -> handler(click.button()) }
}

internal fun owoPartialTick(tickCounter: net.minecraft.client.render.RenderTickCounter): Float =
    tickCounter.getGameTimeDeltaPartialTick(false)

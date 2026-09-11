package axion.client.ui

import net.minecraft.client.gui.DrawContext

/**
 * Draws [block] on a GUI layer lifted to depth [z].
 *
 * Up to 1.21.5 the GUI is depth-sorted, and item icons render well above z=0, so
 * anything that must sit over them (tooltips over saved-hotbar items) has to be
 * lifted explicitly — vanilla puts its own tooltips at z=400. This replaces
 * reflective push/translate calls that looked the MatrixStack methods up by
 * name: production jars run on intermediary names, so those silently did
 * nothing outside a dev environment.
 */
inline fun DrawContext.withGuiLayer(z: Float, block: () -> Unit) {
    val stack = matrices
    stack.push()
    try {
        stack.translate(0.0f, 0.0f, z)
        block()
    } finally {
        stack.pop()
    }
}

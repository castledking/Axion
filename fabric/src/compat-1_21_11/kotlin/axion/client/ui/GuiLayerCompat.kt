package axion.client.ui

import net.minecraft.client.gui.DrawContext

/**
 * Draws [block] on its own GUI matrix layer.
 *
 * From 1.21.6 the GUI stack is 2D (Matrix3x2fStack) and elements render in
 * submission order, so [z] has nothing to do: drawing later is what puts
 * something on top. The signature matches the pre-1.21.6 version, where the
 * GUI is depth-sorted and [z] does the lifting.
 */
@Suppress("UNUSED_PARAMETER")
inline fun DrawContext.withGuiLayer(z: Float, block: () -> Unit) {
    val stack = matrices
    stack.pushMatrix()
    try {
        block()
    } finally {
        stack.popMatrix()
    }
}

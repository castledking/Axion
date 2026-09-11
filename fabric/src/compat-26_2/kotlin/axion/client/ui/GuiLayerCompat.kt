package axion.client.ui

import net.minecraft.client.gui.DrawContext

/**
 * Draws [block] on its own GUI matrix layer.
 *
 * 26.x's GUI stack is 2D (Matrix3x2fStack) and elements render in submission
 * order, so [z] has nothing to do: drawing later is what puts something on top.
 * The signature matches the pre-1.21.6 version, where the GUI is depth-sorted.
 */
@Suppress("UNUSED_PARAMETER")
inline fun DrawContext.withGuiLayer(z: Float, block: () -> Unit) {
    // DrawContext is Axion's GuiGraphics wrapper on 26.x; its adapter's
    // push/pop drive the underlying Matrix3x2fStack.
    val stack = matrices
    stack.push()
    try {
        block()
    } finally {
        stack.pop()
    }
}

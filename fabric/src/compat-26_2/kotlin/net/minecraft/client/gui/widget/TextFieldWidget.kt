package net.minecraft.client.gui.widget

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

class TextFieldWidget(
    textRenderer: TextRenderer,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Text,
) : net.minecraft.client.gui.components.EditBox(textRenderer, x, y, width, height, message) {
    var text: String
        get() = value
        set(value) {
            setValue(value)
        }

    fun setChangedListener(listener: (String) -> Unit) {
        setResponder(listener)
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        extractRenderState(context.delegate, mouseX, mouseY, deltaTicks)
    }
}

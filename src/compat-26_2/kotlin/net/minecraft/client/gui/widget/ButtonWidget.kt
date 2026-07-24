package net.minecraft.client.gui.widget

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.text.Text

class ButtonWidget private constructor(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Text,
    onPress: net.minecraft.client.gui.components.Button.OnPress,
) : net.minecraft.client.gui.components.Button(x, y, width, height, message, onPress, DEFAULT_NARRATION) {
    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        extractDefaultSprite(graphics)
        extractDefaultLabel(graphics.textRenderer())
    }

    class Builder internal constructor(
        private val message: Text,
        private val onPress: net.minecraft.client.gui.components.Button.OnPress,
    ) {
        private var x = 0
        private var y = 0
        private var width = DEFAULT_WIDTH
        private var height = DEFAULT_HEIGHT

        fun dimensions(x: Int, y: Int, width: Int, height: Int): Builder {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            return this
        }

        fun build(): ButtonWidget {
            return ButtonWidget(x, y, width, height, message, onPress)
        }
    }

    companion object {
        fun builder(message: Text, onPress: net.minecraft.client.gui.components.Button.OnPress): Builder {
            return Builder(message, onPress)
        }
    }
}

package net.minecraft.client.gui

import net.minecraft.client.gl.RenderPipelines
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.joml.Matrix3x2fStack

class DrawContext(
    internal val delegate: GuiGraphicsExtractor,
) {
    class MatrixStackAdapter(private val delegate: Matrix3x2fStack) {
        fun push() = delegate.pushMatrix()
        fun pop() = delegate.popMatrix()
        fun translate(x: Double, y: Double, z: Double) = delegate.translate(x.toFloat(), y.toFloat())
    }

    val scaledWindowWidth: Int
        get() = delegate.guiWidth()

    val scaledWindowHeight: Int
        get() = delegate.guiHeight()

    val matrices: MatrixStackAdapter
        get() = MatrixStackAdapter(delegate.pose())

    fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        delegate.fill(x1, y1, x2, y2, color)
    }

    fun drawHorizontalLine(x1: Int, x2: Int, y: Int, color: Int) {
        delegate.horizontalLine(x1, x2, y, color)
    }

    fun drawVerticalLine(x: Int, y1: Int, y2: Int, color: Int) {
        delegate.verticalLine(x, y1, y2, color)
    }

    fun drawCenteredTextWithShadow(textRenderer: Font, text: Text, centerX: Int, y: Int, color: Int) {
        delegate.centeredText(textRenderer, text, centerX, y, color)
    }

    fun drawCenteredTextWithShadow(textRenderer: Font, text: String, centerX: Int, y: Int, color: Int) {
        delegate.centeredText(textRenderer, text, centerX, y, color)
    }

    fun drawTextWithShadow(textRenderer: Font, text: Text, x: Int, y: Int, color: Int) {
        delegate.text(textRenderer, text, x, y, color)
    }

    fun drawTextWithShadow(textRenderer: Font, text: String, x: Int, y: Int, color: Int) {
        delegate.text(textRenderer, text, x, y, color)
    }

    fun drawItem(stack: ItemStack, x: Int, y: Int) {
        delegate.item(stack, x, y)
    }

    fun drawTooltip(textRenderer: Font, text: Text, x: Int, y: Int) {
        delegate.setTooltipForNextFrame(textRenderer, text, x, y)
    }

    fun drawTooltip(textRenderer: Font, lines: List<Text>, x: Int, y: Int) {
        delegate.setComponentTooltipForNextFrame(textRenderer, lines, x, y)
    }

    fun drawStackOverlay(textRenderer: Font, stack: ItemStack, x: Int, y: Int) {
        delegate.itemDecorations(textRenderer, stack, x, y)
    }

    fun drawGuiTexture(texture: Identifier, x: Int, y: Int, width: Int, height: Int) {
        delegate.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height)
    }
}

package axion.client.ui

import net.minecraft.client.gui.DrawContext

fun DrawContext.drawStrokedRectangleCompat(x: Int, y: Int, width: Int, height: Int, color: Int) {
    if (width <= 0 || height <= 0) {
        return
    }

    val maxX = x + width - 1
    val maxY = y + height - 1
    drawHorizontalLine(x, maxX, y, color)
    drawHorizontalLine(x, maxX, maxY, color)
    drawVerticalLine(x, y, maxY, color)
    drawVerticalLine(maxX, y, maxY, color)
}

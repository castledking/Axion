package axion.client.ui

import net.minecraft.client.gui.GuiGraphics

fun GuiGraphics.drawStrokedRectangleCompat(x: Int, y: Int, width: Int, height: Int, color: Int) {
    if (width <= 0 || height <= 0) {
        return
    }

    val maxX = x + width - 1
    val maxY = y + height - 1
    hLine(x, maxX, y, color)
    hLine(x, maxX, maxY, color)
    vLine(x, y, maxY, color)
    vLine(maxX, y, maxY, color)
}

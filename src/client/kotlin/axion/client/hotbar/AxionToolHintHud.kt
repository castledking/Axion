package axion.client.hotbar

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Formatting

object AxionToolHintHud {
    private const val PANEL_BACKGROUND: Int = 0xAA111111.toInt()
    private const val PANEL_BORDER: Int = 0xFF5A5A5A.toInt()
    private const val TITLE_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val SUBTITLE_COLOR: Int = 0xFFE7D39B.toInt()
    private const val INPUT_COLOR: Int = 0xFFBDEFFF.toInt()
    private const val ACTION_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val STATUS_COLOR: Int = 0xFFD2E2E8.toInt()
    private const val FOOTER_COLOR: Int = 0xFFB7B7B7.toInt()
    private const val PANEL_X: Int = 8
    private const val PANEL_BOTTOM_MARGIN: Int = 72
    private const val PADDING: Int = 6
    private const val ENTRY_GAP: Int = 2
    private const val MIN_WIDTH: Int = 176

    fun render(context: DrawContext, tickCounter: net.minecraft.client.render.RenderTickCounter) {
        val client = MinecraftClient.getInstance()
        client.player ?: return
        if (client.options.hudHidden) {
            return
        }

        val panel = AxionToolHintProvider.currentPanel() ?: return
        val textRenderer = client.textRenderer
        val lineHeight = textRenderer.fontHeight
        val contentWidth = sequenceOf(
            textRenderer.getWidth(panel.title),
            panel.subtitle?.let(textRenderer::getWidth) ?: 0,
            panel.footer?.let(textRenderer::getWidth) ?: 0,
            *panel.statusLines.map(textRenderer::getWidth).toTypedArray(),
            *panel.entries.map { textRenderer.getWidth(it.input) + 8 + textRenderer.getWidth(it.action) }.toTypedArray(),
        ).maxOrNull()?.coerceAtLeast(MIN_WIDTH) ?: MIN_WIDTH
        val entryLines = panel.entries.size
        val statusLines = panel.statusLines.size
        val totalLines = 1 +
            (if (panel.subtitle != null) 1 else 0) +
            entryLines +
            statusLines +
            (if (panel.footer != null) 1 else 0)
        val height = (PADDING * 2) + (totalLines * lineHeight) + (maxOf(0, totalLines - 1) * ENTRY_GAP)
        val width = contentWidth + (PADDING * 2)
        val x = PANEL_X
        val y = context.scaledWindowHeight - PANEL_BOTTOM_MARGIN - height

        context.fill(x, y, x + width, y + height, PANEL_BACKGROUND)
        context.drawStrokedRectangle(x, y, width, height, PANEL_BORDER)

        var cursorY = y + PADDING
        context.drawTextWithShadow(textRenderer, panel.title, x + PADDING, cursorY, TITLE_COLOR)
        cursorY += lineHeight + ENTRY_GAP

        panel.subtitle?.let { subtitle ->
            context.drawTextWithShadow(textRenderer, subtitle, x + PADDING, cursorY, SUBTITLE_COLOR)
            cursorY += lineHeight + ENTRY_GAP
        }

        panel.entries.forEach { entry ->
            context.drawTextWithShadow(textRenderer, Formatting.AQUA.toString() + entry.input, x + PADDING, cursorY, INPUT_COLOR)
            context.drawTextWithShadow(textRenderer, entry.action, x + PADDING + 54, cursorY, ACTION_COLOR)
            cursorY += lineHeight + ENTRY_GAP
        }

        panel.statusLines.forEach { statusLine ->
            context.drawTextWithShadow(textRenderer, statusLine, x + PADDING, cursorY, STATUS_COLOR)
            cursorY += lineHeight + ENTRY_GAP
        }

        panel.footer?.let { footer ->
            context.drawTextWithShadow(textRenderer, footer, x + PADDING, cursorY, FOOTER_COLOR)
        }
    }
}

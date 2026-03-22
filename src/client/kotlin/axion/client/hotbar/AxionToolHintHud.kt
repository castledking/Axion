package axion.client.hotbar

import axion.client.config.AxionConfigScreen
import axion.client.config.MagicSelectCustomMaskScreen
import axion.client.config.MagicSelectMaskConfigScreen
import axion.client.config.MagicSelectTemplateEditScreen
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.font.TextRenderer
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
        if (client.options.hudHidden || shouldSuppressForScreen(client.currentScreen)) {
            return
        }

        val panel = AxionToolHintProvider.currentPanel() ?: return
        val textRenderer = client.textRenderer
        val lineHeight = textRenderer.fontHeight
        val contentWidth = computeContentWidth(textRenderer, panel)
        val totalLines = computeTotalLines(panel)
        val height = (PADDING * 2) + (totalLines * lineHeight) + (maxOf(0, totalLines - 1) * ENTRY_GAP)
        val width = contentWidth + (PADDING * 2)
        val x = PANEL_X
        val y = context.scaledWindowHeight - PANEL_BOTTOM_MARGIN - height

        context.fill(x, y, x + width, y + height, PANEL_BACKGROUND)
        context.drawStrokedRectangle(x, y, width, height, PANEL_BORDER)

        var cursorY = y + PADDING
        cursorY = drawHeader(context, textRenderer, panel, x, cursorY, lineHeight)
        cursorY = drawEntries(context, textRenderer, panel, x, cursorY, lineHeight)
        cursorY = drawStatusLines(context, textRenderer, panel, x, cursorY, lineHeight)
        drawFooter(context, textRenderer, panel, x, cursorY)
    }

    private fun shouldSuppressForScreen(screen: net.minecraft.client.gui.screen.Screen?): Boolean {
        return screen is AxionConfigScreen ||
            screen is MagicSelectMaskConfigScreen ||
            screen is MagicSelectTemplateEditScreen ||
            screen is MagicSelectCustomMaskScreen
    }

    private fun computeContentWidth(textRenderer: TextRenderer, panel: ToolHintPanel): Int {
        var maxWidth = MIN_WIDTH
        maxWidth = maxOf(maxWidth, textRenderer.getWidth(panel.title))
        panel.subtitle?.let { maxWidth = maxOf(maxWidth, textRenderer.getWidth(it)) }
        panel.footer?.let { maxWidth = maxOf(maxWidth, textRenderer.getWidth(it)) }
        panel.statusLines.forEach { statusLine ->
            maxWidth = maxOf(maxWidth, textRenderer.getWidth(statusLine))
        }
        panel.entries.forEach { entry ->
            val entryWidth = textRenderer.getWidth(entry.input) + 8 + textRenderer.getWidth(entry.action)
            maxWidth = maxOf(maxWidth, entryWidth)
        }
        return maxWidth
    }

    private fun computeTotalLines(panel: ToolHintPanel): Int {
        var total = 1 + panel.entries.size + panel.statusLines.size
        if (panel.subtitle != null) {
            total += 1
        }
        if (panel.footer != null) {
            total += 1
        }
        return total
    }

    private fun drawHeader(
        context: DrawContext,
        textRenderer: TextRenderer,
        panel: ToolHintPanel,
        x: Int,
        startY: Int,
        lineHeight: Int,
    ): Int {
        var cursorY = startY
        context.drawTextWithShadow(textRenderer, panel.title, x + PADDING, cursorY, TITLE_COLOR)
        cursorY += lineHeight + ENTRY_GAP
        panel.subtitle?.let { subtitle ->
            context.drawTextWithShadow(textRenderer, subtitle, x + PADDING, cursorY, SUBTITLE_COLOR)
            cursorY += lineHeight + ENTRY_GAP
        }
        return cursorY
    }

    private fun drawEntries(
        context: DrawContext,
        textRenderer: TextRenderer,
        panel: ToolHintPanel,
        x: Int,
        startY: Int,
        lineHeight: Int,
    ): Int {
        var cursorY = startY
        panel.entries.forEach { entry ->
            context.drawTextWithShadow(textRenderer, Formatting.AQUA.toString() + entry.input, x + PADDING, cursorY, INPUT_COLOR)
            context.drawTextWithShadow(textRenderer, entry.action, x + PADDING + 54, cursorY, ACTION_COLOR)
            cursorY += lineHeight + ENTRY_GAP
        }
        return cursorY
    }

    private fun drawStatusLines(
        context: DrawContext,
        textRenderer: TextRenderer,
        panel: ToolHintPanel,
        x: Int,
        startY: Int,
        lineHeight: Int,
    ): Int {
        var cursorY = startY
        panel.statusLines.forEach { statusLine ->
            context.drawTextWithShadow(textRenderer, statusLine, x + PADDING, cursorY, STATUS_COLOR)
            cursorY += lineHeight + ENTRY_GAP
        }
        return cursorY
    }

    private fun drawFooter(
        context: DrawContext,
        textRenderer: TextRenderer,
        panel: ToolHintPanel,
        x: Int,
        cursorY: Int,
    ) {
        panel.footer?.let { footer ->
            context.drawTextWithShadow(textRenderer, footer, x + PADDING, cursorY, FOOTER_COLOR)
        }
    }
}

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
    private const val TARGET_STATUS_COLOR: Int = 0xFF8C9498.toInt()
    private const val FOOTER_COLOR: Int = 0xFFB7B7B7.toInt()
    private const val PANEL_X: Int = 8
    private const val PANEL_BOTTOM_MARGIN: Int = 72
    private const val PADDING: Int = 6
    private const val ENTRY_GAP: Int = 2
    private const val SECTION_GAP: Int = 5
    private const val INPUT_ACTION_GAP: Int = 12
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
        val layout = computeLayout(textRenderer, panel)
        val height = computeHeight(panel, lineHeight)
        val width = layout.contentWidth + (PADDING * 2)
        val x = PANEL_X
        val y = context.scaledWindowHeight - PANEL_BOTTOM_MARGIN - height

        context.fill(x, y, x + width, y + height, PANEL_BACKGROUND)
        context.drawStrokedRectangle(x, y, width, height, PANEL_BORDER)

        var cursorY = y + PADDING
        cursorY = drawHeader(context, textRenderer, panel, x, cursorY, lineHeight)
        cursorY = drawEntries(context, textRenderer, panel, x, cursorY, lineHeight, layout)
        cursorY = drawStatusLines(context, textRenderer, panel, x, cursorY, lineHeight)
        drawFooter(context, textRenderer, panel, x, cursorY)
    }

    private fun shouldSuppressForScreen(screen: net.minecraft.client.gui.screen.Screen?): Boolean {
        return screen is AxionConfigScreen ||
            screen is MagicSelectMaskConfigScreen ||
            screen is MagicSelectTemplateEditScreen ||
            screen is MagicSelectCustomMaskScreen
    }

    private fun computeLayout(textRenderer: TextRenderer, panel: ToolHintPanel): HintLayout {
        var maxWidth = MIN_WIDTH
        var maxInputWidth = 0
        maxWidth = maxOf(maxWidth, textRenderer.getWidth(panel.title))
        panel.subtitle?.let { maxWidth = maxOf(maxWidth, textRenderer.getWidth(it)) }
        panel.footer?.let { maxWidth = maxOf(maxWidth, textRenderer.getWidth(it)) }
        panel.statusLines.forEach { statusLine ->
            maxWidth = maxOf(maxWidth, textRenderer.getWidth(statusLine))
        }
        panel.entries.forEach { entry ->
            val inputWidth = textRenderer.getWidth(entry.input)
            val entryWidth = inputWidth + INPUT_ACTION_GAP + textRenderer.getWidth(entry.action)
            maxWidth = maxOf(maxWidth, entryWidth)
            if (!entry.inline) {
                maxInputWidth = maxOf(maxInputWidth, inputWidth)
            }
        }
        return HintLayout(
            contentWidth = maxWidth,
            actionOffset = maxInputWidth + INPUT_ACTION_GAP,
        )
    }

    private fun computeHeight(panel: ToolHintPanel, lineHeight: Int): Int {
        var height = PADDING * 2
        height += lineHeight
        if (panel.subtitle != null) {
            height += ENTRY_GAP + lineHeight
        }
        if (panel.entries.isNotEmpty()) {
            height += ENTRY_GAP + (panel.entries.size * lineHeight) + ((panel.entries.size - 1) * ENTRY_GAP)
        }
        if (panel.statusLines.isNotEmpty()) {
            height += SECTION_GAP + (panel.statusLines.size * lineHeight) + ((panel.statusLines.size - 1) * ENTRY_GAP)
        }
        if (panel.footer != null) {
            height += SECTION_GAP + lineHeight
        }
        return height
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
        layout: HintLayout,
    ): Int {
        var cursorY = startY
        panel.entries.forEach { entry ->
            val inputX = x + PADDING
            context.drawTextWithShadow(textRenderer, Formatting.AQUA.toString() + entry.input, inputX, cursorY, INPUT_COLOR)
            val actionX = if (entry.inline) {
                inputX + textRenderer.getWidth(entry.input) + INPUT_ACTION_GAP
            } else {
                inputX + layout.actionOffset
            }
            context.drawTextWithShadow(textRenderer, entry.action, actionX, cursorY, ACTION_COLOR)
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
        if (panel.statusLines.isNotEmpty()) {
            cursorY += SECTION_GAP - ENTRY_GAP
        }
        panel.statusLines.forEach { statusLine ->
            val color = if (statusLine.string.startsWith("Target:")) TARGET_STATUS_COLOR else STATUS_COLOR
            context.drawTextWithShadow(textRenderer, statusLine, x + PADDING, cursorY, color)
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
            val footerY = if (panel.statusLines.isNotEmpty()) cursorY + (SECTION_GAP - ENTRY_GAP) else cursorY
            context.drawTextWithShadow(textRenderer, footer, x + PADDING, footerY, FOOTER_COLOR)
        }
    }

    private data class HintLayout(
        val contentWidth: Int,
        val actionOffset: Int,
    )
}

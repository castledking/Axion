package axion.client.hotbar

import axion.client.compat.VersionCompatImpl
import axion.client.config.AxionConfigScreen
import axion.client.config.MagicSelectCustomMaskScreen
import axion.client.config.MagicSelectMaskConfigScreen
import axion.client.config.MagicSelectTemplateEditScreen
import axion.client.ui.drawStrokedRectangleCompat
import axion.common.compat.VersionCompat
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier

object AxionToolHintHud {
    private const val PANEL_BACKGROUND: Int = 0x8A0D0F10.toInt()
    private const val PANEL_BORDER: Int = 0x885F686D.toInt()
    private const val TEXT_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val INPUT_COLOR: Int = 0xFFBDEFFF.toInt()
    private const val HOTBAR_STATUS_COLOR: Int = 0xFFE7D39B.toInt()
    private const val ICON_SIZE: Int = 16
    private const val ICON_TEXT_GAP: Int = 4
    private const val CROSSHAIR_ROW_GAP: Int = 2
    private const val CROSSHAIR_TOP_OFFSET: Int = 18
    private const val PANEL_PADDING_X: Int = 6
    private const val PANEL_PADDING_Y: Int = 5
    private const val KEY_LINE_GAP: Int = 2
    private const val RIGHT_MARGIN: Int = 8
    private const val BOTTOM_MARGIN: Int = 72

    private val mouseTextures: Map<MouseHintIcon, Identifier> by lazy {
        mapOf(
            MouseHintIcon.LEFT to VersionCompat.INSTANCE.identifierOf("axion", "mouse/left.png"),
            MouseHintIcon.RIGHT to VersionCompat.INSTANCE.identifierOf("axion", "mouse/right.png"),
            MouseHintIcon.SCROLL to VersionCompat.INSTANCE.identifierOf("axion", "mouse/scroll.png"),
            MouseHintIcon.NEUTRAL to VersionCompat.INSTANCE.identifierOf("axion", "mouse/neutral.png"),
        )
    }

    fun render(context: DrawContext, tickCounter: net.minecraft.client.render.RenderTickCounter) {
        val client = MinecraftClient.getInstance()
        client.player ?: return
        if (client.options.hudHidden || shouldSuppressForScreen(client.currentScreen)) {
            return
        }

        val hints = AxionToolHintProvider.currentCompactHints() ?: return
        val textRenderer = client.textRenderer

        renderCrosshairHints(context, textRenderer, hints.crosshairHints)
        renderHotbarStatus(context, textRenderer, hints.hotbarStatus)
        renderKeyHints(context, textRenderer, hints.keyHints)
    }

    private fun shouldSuppressForScreen(screen: net.minecraft.client.gui.screen.Screen?): Boolean {
        return screen is AxionConfigScreen ||
            screen is MagicSelectMaskConfigScreen ||
            screen is MagicSelectTemplateEditScreen ||
            screen is MagicSelectCustomMaskScreen
    }

    private fun renderCrosshairHints(
        context: DrawContext,
        textRenderer: TextRenderer,
        hints: List<CrosshairHint>,
    ) {
        if (hints.isEmpty()) {
            return
        }

        val centerX = context.scaledWindowWidth / 2
        val rowHeight = ICON_SIZE + CROSSHAIR_ROW_GAP
        var y = (context.scaledWindowHeight / 2) + CROSSHAIR_TOP_OFFSET

        hints.forEach { hint ->
            when (hint) {
                is CrosshairHint.Mouse -> {
                    val actionWidth = textRenderer.getWidth(hint.action)
                    val totalWidth = ICON_SIZE + ICON_TEXT_GAP + actionWidth
                    val x = centerX - totalWidth / 2
                    mouseTextures[hint.icon]?.let { texture ->
                        VersionCompatImpl.drawGuiTexture(context, texture, x, y, ICON_SIZE, ICON_SIZE)
                    }
                    val textY = y + (ICON_SIZE - textRenderer.fontHeight) / 2 + 1
                    context.drawTextWithShadow(
                        textRenderer,
                        hint.action,
                        x + ICON_SIZE + ICON_TEXT_GAP,
                        textY,
                        TEXT_COLOR,
                    )
                }
                is CrosshairHint.Key -> {
                    val separator = " - "
                    val keyWidth = textRenderer.getWidth(hint.key)
                    val sepWidth = textRenderer.getWidth(separator)
                    val actionWidth = textRenderer.getWidth(hint.action)
                    val totalWidth = keyWidth + sepWidth + actionWidth
                    val x = centerX - totalWidth / 2
                    val textY = y + (ICON_SIZE - textRenderer.fontHeight) / 2 + 1
                    context.drawTextWithShadow(textRenderer, hint.key, x, textY, INPUT_COLOR)
                    context.drawTextWithShadow(textRenderer, separator, x + keyWidth, textY, TEXT_COLOR)
                    context.drawTextWithShadow(textRenderer, hint.action, x + keyWidth + sepWidth, textY, TEXT_COLOR)
                }
            }
            y += rowHeight
        }
    }

    private fun renderHotbarStatus(
        context: DrawContext,
        textRenderer: TextRenderer,
        status: String?,
    ) {
        if (status == null) {
            return
        }

        val width = textRenderer.getWidth(status)
        val x = (context.scaledWindowWidth - width) / 2
        val y = context.scaledWindowHeight - 48
        context.fill(
            x - PANEL_PADDING_X,
            y - PANEL_PADDING_Y,
            x + width + PANEL_PADDING_X,
            y + textRenderer.fontHeight + PANEL_PADDING_Y,
            PANEL_BACKGROUND,
        )
        context.drawTextWithShadow(textRenderer, status, x, y, HOTBAR_STATUS_COLOR)
    }

    private fun renderKeyHints(
        context: DrawContext,
        textRenderer: TextRenderer,
        hints: List<ToolHintEntry>,
    ) {
        if (hints.isEmpty()) {
            return
        }

        val lines = hints.map { "${it.input} - ${it.action}" }
        val contentWidth = lines.maxOf(textRenderer::getWidth)
        val lineHeight = textRenderer.fontHeight
        val panelWidth = contentWidth + (PANEL_PADDING_X * 2)
        val panelHeight = (lineHeight * lines.size) + (KEY_LINE_GAP * (lines.size - 1)) + (PANEL_PADDING_Y * 2)
        val x = context.scaledWindowWidth - RIGHT_MARGIN - panelWidth
        val y = context.scaledWindowHeight - BOTTOM_MARGIN - panelHeight

        context.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BACKGROUND)
        context.drawStrokedRectangleCompat(x, y, panelWidth, panelHeight, PANEL_BORDER)

        var cursorY = y + PANEL_PADDING_Y
        hints.forEach { hint ->
            val input = hint.input
            val separator = " - "
            val action = hint.action
            val textX = x + PANEL_PADDING_X
            context.drawTextWithShadow(textRenderer, input, textX, cursorY, INPUT_COLOR)
            val actionX = textX + textRenderer.getWidth(input + separator)
            context.drawTextWithShadow(textRenderer, separator, textX + textRenderer.getWidth(input), cursorY, TEXT_COLOR)
            context.drawTextWithShadow(textRenderer, action, actionX, cursorY, TEXT_COLOR)
            cursorY += lineHeight + KEY_LINE_GAP
        }
    }
}

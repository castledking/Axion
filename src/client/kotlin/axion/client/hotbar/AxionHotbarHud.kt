package axion.client.hotbar

import axion.client.input.AxionModifierKeys
import axion.client.tool.AxionToolSelectionController
import axion.common.model.AxionSubtool
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Formatting

object AxionHotbarHud {
    private const val OUTER_BACKGROUND: Int = 0xB0101010.toInt()
    private const val INNER_BACKGROUND: Int = 0xAA1E1E1E.toInt()
    private const val BORDER_NEUTRAL: Int = 0xFF7C7C7C.toInt()
    private const val BORDER_SELECTED: Int = 0xFFFFFFFF.toInt()
    private const val TEXT_SELECTED: Int = 0xFFFFFFFF.toInt()
    private const val TEXT_IDLE: Int = 0xFFE2C884.toInt()
    private const val STRIP_ENTRY_HEIGHT: Int = 18
    private const val STRIP_ENTRY_WIDTH: Int = 42
    private const val STRIP_ENTRY_GAP: Int = 2

    fun render(context: DrawContext, tickCounter: net.minecraft.client.render.RenderTickCounter) {
        val client = MinecraftClient.getInstance()
        client.player ?: return
        if (client.options.hudHidden) {
            return
        }

        val sideSlot = AxionHudLayout.sideSlot(
            client = client,
            screenWidth = context.scaledWindowWidth,
            screenHeight = context.scaledWindowHeight,
        )

        val axionSelected = AxionToolSelectionController.isAxionSelected()
        val activeSubtool = AxionToolSelectionController.selectedSubtool()
        renderSlot(
            context = context,
            x = sideSlot.x,
            y = sideSlot.y,
            size = sideSlot.size,
            selected = axionSelected,
            label = if (axionSelected) activeSubtool.shortLabel else "Ax",
        )

        if (axionSelected && AxionModifierKeys.isAltDown(client)) {
            renderSubtoolStrip(
                context = context,
                sideSlot = sideSlot,
                selected = activeSubtool,
                textRenderer = client.textRenderer,
            )
        }
    }

    private fun renderSlot(
        context: DrawContext,
        x: Int,
        y: Int,
        size: Int,
        selected: Boolean,
        label: String,
    ) {
        val borderColor = if (selected) BORDER_SELECTED else BORDER_NEUTRAL
        context.fill(x, y, x + size, y + size, OUTER_BACKGROUND)
        context.fill(x + 2, y + 2, x + size - 2, y + size - 2, INNER_BACKGROUND)
        context.drawStrokedRectangle(x, y, size, size, borderColor)
        context.drawCenteredTextWithShadow(
            MinecraftClient.getInstance().textRenderer,
            label,
            x + (size / 2),
            y + 8,
            if (selected) TEXT_SELECTED else TEXT_IDLE,
        )
    }

    private fun renderSubtoolStrip(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
        selected: AxionSubtool,
        textRenderer: net.minecraft.client.font.TextRenderer,
    ) {
        val (originX, originBottom) = AxionHudLayout.stripOrigin(sideSlot)

        AxionSubtool.entries.forEachIndexed { index, subtool ->
            val boxY = originBottom - ((index + 1) * (STRIP_ENTRY_HEIGHT + STRIP_ENTRY_GAP))
            val highlighted = subtool == selected
            val borderColor = if (highlighted) BORDER_SELECTED else BORDER_NEUTRAL
            val textColor = if (highlighted) TEXT_SELECTED else TEXT_IDLE

            context.fill(originX - (STRIP_ENTRY_WIDTH - sideSlot.size), boxY, originX + sideSlot.size, boxY + STRIP_ENTRY_HEIGHT, OUTER_BACKGROUND)
            context.drawStrokedRectangle(originX - (STRIP_ENTRY_WIDTH - sideSlot.size), boxY, STRIP_ENTRY_WIDTH, STRIP_ENTRY_HEIGHT, borderColor)
            context.drawTextWithShadow(
                textRenderer,
                subtool.shortLabel,
                originX - (STRIP_ENTRY_WIDTH - sideSlot.size) + 4,
                boxY + 5,
                textColor,
            )
            context.drawTextWithShadow(
                textRenderer,
                Formatting.GRAY.toString() + subtool.displayName,
                originX - (STRIP_ENTRY_WIDTH - sideSlot.size) + 18,
                boxY + 5,
                textColor,
            )
        }
    }
}

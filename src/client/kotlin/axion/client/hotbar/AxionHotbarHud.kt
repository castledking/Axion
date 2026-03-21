package axion.client.hotbar

import axion.client.AxionClientState
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
    private const val BORDER_HOVER: Int = 0xFFD8D8D8.toInt()
    private const val BORDER_SELECTED: Int = 0xFFFFFFFF.toInt()
    private const val TEXT_SELECTED: Int = 0xFFFFFFFF.toInt()
    private const val TEXT_IDLE: Int = 0xFFE2C884.toInt()

    fun render(context: DrawContext, tickCounter: net.minecraft.client.render.RenderTickCounter) {
        val client = MinecraftClient.getInstance()
        client.player ?: return
        if (client.options.hudHidden) {
            return
        }
        if (!AxionToolSelectionController.isCreativeModeAllowed()) {
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
                hovered = AxionAltMenuController.hoveredSubtool(client, context.scaledWindowWidth, context.scaledWindowHeight),
                textRenderer = client.textRenderer,
            )
            renderMiddleClickToggle(
                context = context,
                sideSlot = sideSlot,
                hovered = AxionAltMenuController.isHoveringMiddleClickToggle(
                    client,
                    context.scaledWindowWidth,
                    context.scaledWindowHeight,
                ),
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
        hovered: AxionSubtool?,
        textRenderer: net.minecraft.client.font.TextRenderer,
    ) {
        AxionHudLayout.stripEntries(sideSlot).forEach { entry ->
            val highlighted = entry.subtool == selected
            val hover = entry.subtool == hovered
            val borderColor = when {
                highlighted -> BORDER_SELECTED
                hover -> BORDER_HOVER
                else -> BORDER_NEUTRAL
            }
            val textColor = if (highlighted || hover) TEXT_SELECTED else TEXT_IDLE

            context.fill(entry.x, entry.y, entry.x + entry.width, entry.y + entry.height, OUTER_BACKGROUND)
            context.drawStrokedRectangle(entry.x, entry.y, entry.width, entry.height, borderColor)
            context.drawTextWithShadow(
                textRenderer,
                entry.subtool.shortLabel,
                entry.x + 4,
                entry.y + 5,
                textColor,
            )
            context.drawTextWithShadow(
                textRenderer,
                Formatting.GRAY.toString() + entry.subtool.displayName,
                entry.x + 18,
                entry.y + 5,
                textColor,
            )
        }
    }

    private fun renderMiddleClickToggle(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
        hovered: Boolean,
        textRenderer: net.minecraft.client.font.TextRenderer,
    ) {
        val bounds = AxionHudLayout.middleClickToggleBounds(sideSlot)
        val enabled = AxionClientState.middleClickMagicSelectEnabled
        val borderColor = when {
            enabled -> BORDER_SELECTED
            hovered -> BORDER_HOVER
            else -> BORDER_NEUTRAL
        }
        val textColor = if (enabled || hovered) TEXT_SELECTED else TEXT_IDLE
        val label = if (enabled) "MMB: Magic Select" else "MMB: Extend Face"

        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, OUTER_BACKGROUND)
        context.drawStrokedRectangle(bounds.x, bounds.y, bounds.width, bounds.height, borderColor)
        context.drawCenteredTextWithShadow(
            textRenderer,
            label,
            bounds.x + (bounds.width / 2),
            bounds.y + 5,
            textColor,
        )
    }
}

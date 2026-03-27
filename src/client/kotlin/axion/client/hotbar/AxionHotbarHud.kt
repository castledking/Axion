package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.input.AxionModifierKeys
import axion.client.tool.AxionToolSelectionController
import axion.common.model.AxionSubtool
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
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

        if (!AxionToolSelectionController.isAxionSelected() && SavedHotbarController.isOverlayActive(client)) {
            renderSavedHotbarOverlay(context, client)
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
            renderKeepExistingToggle(
                context = context,
                sideSlot = sideSlot,
                hovered = AxionAltMenuController.isHoveringKeepExistingToggle(
                    client,
                    context.scaledWindowWidth,
                    context.scaledWindowHeight,
                ),
                textRenderer = client.textRenderer,
            )
            renderCopyEntitiesToggle(
                context = context,
                sideSlot = sideSlot,
                hovered = AxionAltMenuController.isHoveringCopyEntitiesToggle(
                    client,
                    context.scaledWindowWidth,
                    context.scaledWindowHeight,
                ),
                textRenderer = client.textRenderer,
            )
            renderCopyAirToggle(
                context = context,
                sideSlot = sideSlot,
                hovered = AxionAltMenuController.isHoveringCopyAirToggle(
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

    private fun renderSavedHotbarOverlay(
        context: DrawContext,
        client: MinecraftClient,
    ) {
        val page = SavedHotbarController.selectedPage()
        val displayRows = SavedHotbarController.displayHotbarsForSelectedPage(client)
        val rowBounds = AxionHudLayout.savedHotbarRows(context.scaledWindowWidth, context.scaledWindowHeight, page)

        rowBounds.zip(displayRows).forEach { (bounds, display) ->
            val borderColor = when {
                display.selected -> BORDER_SELECTED
                display.active -> TEXT_IDLE
                else -> BORDER_NEUTRAL
            }

            context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, OUTER_BACKGROUND)
            context.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, INNER_BACKGROUND)
            renderSavedHotbarItems(context, bounds.x + 1, bounds.y + 1, display.stacks)
            context.drawStrokedRectangle(bounds.x, bounds.y, bounds.width, bounds.height, borderColor)
        }

        val topBounds = rowBounds.last()
        context.drawTextWithShadow(
            client.textRenderer,
            "Page ${page + 1}",
            topBounds.x + topBounds.width + 8,
            topBounds.y + 2,
            TEXT_SELECTED,
        )
        renderSavedHotbarPageButtons(context, client, page)
    }

    private fun renderSavedHotbarItems(
        context: DrawContext,
        startX: Int,
        startY: Int,
        stacks: List<ItemStack>,
    ) {
        stacks.take(9).forEachIndexed { index, stack ->
            val slotX = startX + (index * 20)
            val slotWidth = if (index == 8) 20 else 19
            context.drawStrokedRectangle(slotX, startY, slotWidth, 19, 0xFF6A6A6A.toInt())
            if (!stack.isEmpty) {
                context.drawItem(stack, slotX + 2, startY + 2)
                context.drawStackOverlay(MinecraftClient.getInstance().textRenderer, stack, slotX + 2, startY + 2)
            }
        }
    }

    private fun renderSavedHotbarPageButtons(
        context: DrawContext,
        client: MinecraftClient,
        page: Int,
    ) {
        val hovered = AxionAltMenuController.hoveringSavedHotbarPageButton(
            client,
            context.scaledWindowWidth,
            context.scaledWindowHeight,
        )
        AxionHudLayout.savedHotbarPageButtons(context.scaledWindowWidth, context.scaledWindowHeight, page).forEach { button ->
            val isHovered = hovered?.direction == button.direction
            val borderColor = if (isHovered) BORDER_HOVER else BORDER_NEUTRAL
            context.fill(button.x, button.y, button.x + button.width, button.y + button.height, OUTER_BACKGROUND)
            context.fill(button.x + 1, button.y + 1, button.x + button.width - 1, button.y + button.height - 1, INNER_BACKGROUND)
            context.drawStrokedRectangle(button.x, button.y, button.width, button.height, borderColor)
            context.drawCenteredTextWithShadow(
                client.textRenderer,
                if (button.direction > 0) "↑" else "↓",
                button.x + (button.width / 2),
                button.y + 2,
                if (isHovered) TEXT_SELECTED else TEXT_IDLE,
            )
        }
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

    private fun renderKeepExistingToggle(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
        hovered: Boolean,
        textRenderer: net.minecraft.client.font.TextRenderer,
    ) {
        val bounds = AxionHudLayout.keepExistingToggleBounds(sideSlot)
        val enabled = AxionClientState.keepExistingEnabled
        val borderColor = when {
            enabled -> BORDER_SELECTED
            hovered -> BORDER_HOVER
            else -> BORDER_NEUTRAL
        }
        val textColor = if (enabled || hovered) TEXT_SELECTED else TEXT_IDLE
        val label = if (enabled) "Keep Existing: ON" else "Keep Existing: OFF"

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

    private fun renderCopyEntitiesToggle(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
        hovered: Boolean,
        textRenderer: net.minecraft.client.font.TextRenderer,
    ) {
        val bounds = AxionHudLayout.copyEntitiesToggleBounds(sideSlot)
        val enabled = AxionClientState.copyEntitiesEnabled
        val borderColor = when {
            enabled -> BORDER_SELECTED
            hovered -> BORDER_HOVER
            else -> BORDER_NEUTRAL
        }
        val textColor = if (enabled || hovered) TEXT_SELECTED else TEXT_IDLE
        val label = if (enabled) "Copy Entities: ON" else "Copy Entities: OFF"

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

    private fun renderCopyAirToggle(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
        hovered: Boolean,
        textRenderer: net.minecraft.client.font.TextRenderer,
    ) {
        val bounds = AxionHudLayout.copyAirToggleBounds(sideSlot)
        val enabled = AxionClientState.copyAirEnabled
        val borderColor = when {
            enabled -> BORDER_SELECTED
            hovered -> BORDER_HOVER
            else -> BORDER_NEUTRAL
        }
        val textColor = if (enabled || hovered) TEXT_SELECTED else TEXT_IDLE
        val label = if (enabled) "Copy Air: ON" else "Copy Air: OFF"

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

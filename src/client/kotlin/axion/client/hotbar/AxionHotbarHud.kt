package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.input.AxionModifierKeys
import axion.client.tool.AxionToolSelectionController
import axion.client.ui.drawStrokedRectangleCompat
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
        context.drawStrokedRectangleCompat(x, y, size, size, borderColor)
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
            context.drawStrokedRectangleCompat(bounds.x, bounds.y, bounds.width, bounds.height, borderColor)
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
        renderFlyingSpeedSlider(context, client, page)
    }

    private fun renderFlyingSpeedSlider(
        context: DrawContext,
        client: MinecraftClient,
        page: Int,
    ) {
        // Only render when player can fly
        if (client.player?.abilities?.allowFlying != true) {
            return
        }

        val bounds = AxionHudLayout.flyingSpeedSliderBounds(context.scaledWindowWidth, context.scaledWindowHeight, page)
        val multiplier = AxionClientState.flySpeedMultiplier

        // Check hover states for each element
        val plusHovered = AxionAltMenuController.isHoveringFlyingSpeedPlusButton(client, context.scaledWindowWidth, context.scaledWindowHeight)
        val trackHovered = AxionAltMenuController.isHoveringFlyingSpeedTrack(client, context.scaledWindowWidth, context.scaledWindowHeight)
        val minusHovered = AxionAltMenuController.isHoveringFlyingSpeedMinusButton(client, context.scaledWindowWidth, context.scaledWindowHeight)

        // Render + button
        val plus = bounds.plusButton
        val plusBorderColor = if (plusHovered) BORDER_HOVER else BORDER_NEUTRAL
        val plusTextColor = if (plusHovered) TEXT_SELECTED else TEXT_IDLE
        context.fill(plus.x, plus.y, plus.x + plus.width, plus.y + plus.height, OUTER_BACKGROUND)
        context.fill(plus.x + 1, plus.y + 1, plus.x + plus.width - 1, plus.y + plus.height - 1, INNER_BACKGROUND)
        context.drawStrokedRectangleCompat(plus.x, plus.y, plus.width, plus.height, plusBorderColor)
        context.drawCenteredTextWithShadow(client.textRenderer, "+", plus.x + plus.width / 2, plus.y + 2, plusTextColor)

        // Percentage label above + button
        val percentageText = "${(multiplier * 100).toInt()}%"
        val labelY = plus.y - client.textRenderer.fontHeight - 2
        context.drawCenteredTextWithShadow(
            client.textRenderer,
            percentageText,
            plus.x + plus.width / 2,
            labelY,
            TEXT_IDLE,
        )

        // Render track
        val track = bounds.track
        val trackBorderColor = if (trackHovered) BORDER_HOVER else BORDER_NEUTRAL
        context.fill(track.x, track.y, track.x + track.width, track.y + track.height, OUTER_BACKGROUND)
        context.fill(track.x + 1, track.y + 1, track.x + track.width - 1, track.y + track.height - 1, INNER_BACKGROUND)

        // Calculate filled portion (bottom to top, 1.0f to 9.99f)
        val normalizedValue = (multiplier - 1.0f) / 8.99f
        val fillHeight = (track.height * normalizedValue).toInt()
        val fillY = track.y + track.height - fillHeight

        // Gradient colors (blue → lime)
        val gradientStops = listOf(
            0.0f to 0xFF1A5CFF.toInt(),   // Deep blue (bottom)
            0.33f to 0xFF00C8FF.toInt(),   // Cyan
            0.55f to 0xFF00E8A0.toInt(),   // Teal-green
            0.75f to 0xFF4DFF4D.toInt(),   // Green
            1.0f to 0xFF99FF00.toInt(),    // Lime (top)
        )

        // Draw gradient fill in horizontal slices
        val sliceHeight = 10
        val numSlices = (fillHeight + sliceHeight - 1) / sliceHeight

        for (i in 0 until numSlices) {
            val sliceY = fillY + (i * sliceHeight)
            val sliceBottom = (sliceY + sliceHeight).coerceAtMost(track.y + track.height - 1)
            val sliceTop = sliceY.coerceAtLeast(track.y + 1)

            if (sliceTop >= sliceBottom) continue

            // Calculate normalized position for this slice (0 = bottom, 1 = top of fill)
            val sliceCenter = (sliceTop + sliceBottom) / 2f
            val sliceNormalized = if (fillHeight > 0) (sliceCenter - (track.y + track.height - fillHeight)) / fillHeight else 0f

            // Interpolate color
            val color = interpolateGradientColor(gradientStops, sliceNormalized.coerceIn(0f, 1f))
            context.fill(track.x + 1, sliceTop, track.x + track.width - 1, sliceBottom, color)
        }

        // Track border
        context.drawStrokedRectangleCompat(track.x, track.y, track.width, track.height, trackBorderColor)

        // Render - button
        val minus = bounds.minusButton
        val minusBorderColor = if (minusHovered) BORDER_HOVER else BORDER_NEUTRAL
        val minusTextColor = if (minusHovered) TEXT_SELECTED else TEXT_IDLE
        context.fill(minus.x, minus.y, minus.x + minus.width, minus.y + minus.height, OUTER_BACKGROUND)
        context.fill(minus.x + 1, minus.y + 1, minus.x + minus.width - 1, minus.y + minus.height - 1, INNER_BACKGROUND)
        context.drawStrokedRectangleCompat(minus.x, minus.y, minus.width, minus.height, minusBorderColor)
        context.drawCenteredTextWithShadow(client.textRenderer, "-", minus.x + minus.width / 2, minus.y + 2, minusTextColor)
    }

    private fun interpolateGradientColor(stops: List<Pair<Float, Int>>, t: Float): Int {
        // Find the two stops to interpolate between
        for (i in 0 until stops.size - 1) {
            val (t1, c1) = stops[i]
            val (t2, c2) = stops[i + 1]
            if (t >= t1 && t <= t2) {
                val localT = (t - t1) / (t2 - t1)
                return interpolateColor(c1, c2, localT)
            }
        }
        return stops.last().second
    }

    private fun interpolateColor(c1: Int, c2: Int, t: Float): Int {
        val a1 = (c1 shr 24) and 0xFF
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF

        val a2 = (c2 shr 24) and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF

        val a = (a1 + (a2 - a1) * t).toInt()
        val r = (r1 + (r2 - r1) * t).toInt()
        val g = (g1 + (g2 - g1) * t).toInt()
        val b = (b1 + (b2 - b1) * t).toInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
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
            context.drawStrokedRectangleCompat(slotX, startY, slotWidth, 19, 0xFF6A6A6A.toInt())
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
            context.drawStrokedRectangleCompat(button.x, button.y, button.width, button.height, borderColor)
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
            context.drawStrokedRectangleCompat(entry.x, entry.y, entry.width, entry.height, borderColor)
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
        context.drawStrokedRectangleCompat(bounds.x, bounds.y, bounds.width, bounds.height, borderColor)
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
        context.drawStrokedRectangleCompat(bounds.x, bounds.y, bounds.width, bounds.height, borderColor)
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
        context.drawStrokedRectangleCompat(bounds.x, bounds.y, bounds.width, bounds.height, borderColor)
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
        context.drawStrokedRectangleCompat(bounds.x, bounds.y, bounds.width, bounds.height, borderColor)
        context.drawCenteredTextWithShadow(
            textRenderer,
            label,
            bounds.x + (bounds.width / 2),
            bounds.y + 5,
            textColor,
        )
    }
}

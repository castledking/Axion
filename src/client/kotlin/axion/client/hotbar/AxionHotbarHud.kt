package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.compat.VersionCompatImpl
import axion.client.input.AxionModifierKeys
import axion.client.tool.AxionToolSelectionController
import axion.client.ui.drawStrokedRectangleCompat
import axion.common.compat.VersionCompat
import axion.common.model.AxionSubtool
import kotlin.math.sqrt
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import net.minecraft.util.Arm
import net.minecraft.util.Identifier

object AxionHotbarHud {
    private val HOTBAR_SWAPPER_TEXTURE: Identifier by lazy {
        VersionCompat.INSTANCE.identifierOf("axion", "gui/hotbar_swapper.png")
    }
    private val TOOL_SWAPPER_TEXTURE: Identifier by lazy {
        VersionCompat.INSTANCE.identifierOf("axion", "gui/tool_swapper.png")
    }
    private const val OUTER_BACKGROUND: Int = 0xB0101010.toInt()
    private const val INNER_BACKGROUND: Int = 0xAA1E1E1E.toInt()
    private const val BORDER_NEUTRAL: Int = 0xFF7C7C7C.toInt()
    private const val BORDER_HOVER: Int = 0xFFD8D8D8.toInt()
    private const val BORDER_SELECTED: Int = 0xFFFFFFFF.toInt()
    private const val TEXT_SELECTED: Int = 0xFFFFFFFF.toInt()
    private const val TEXT_IDLE: Int = 0xFFE2C884.toInt()
    private const val OPAQUE_ALPHA: Int = -0x1000000
    private const val ATLAS_SIZE: Int = 256
    private const val TOOL_SWAPPER_ATLAS_SIZE: Int = 256
    private const val TOOL_STACK_WIDTH: Int = 22
    private const val TOOL_STACK_ROW_HEIGHT: Int = 20
    private const val TOOL_STACK_BASE_HEIGHT: Int = 2
    private const val TOOL_SELECTED_WIDTH: Int = 24
    private const val TOOL_SELECTED_HEIGHT: Int = 24
    private const val TOOL_ICON_SIZE: Int = 16

    // Sprite regions on hotbar_swapper.png (256×256 atlas)
    private val SEL_HIGHLIGHT = SpriteRegion(0, 0, 184, 24)
    private val HOTBAR_GRID_BG = SpriteRegion(74, 74, 182, 182)
    private val FLY_PLUS = SpriteRegion(0, 164, 16, 14)
    private val FLY_PLUS_HOVER = SpriteRegion(32, 164, 16, 14)
    private val FLY_PLUS_CLICK = SpriteRegion(0, 164, 16, 14)
    private val FLY_BAR_FILLED = SpriteRegion(0, 177, 16, 64)
    private val FLY_BAR_EMPTY = SpriteRegion(16, 177, 16, 64)
    private val FLY_MINUS = SpriteRegion(16, 242, 16, 14)
    private val FLY_MINUS_HOVER = SpriteRegion(32, 242, 16, 14)
    private val TOOLBOX_SLOT = SpriteRegion(216, 0, 20, 20)
    private val TOOLBOX_SLOT_HOVER = SpriteRegion(216, 20, 20, 20)
    private val WRENCH = SpriteRegion(0, 104, 16, 16)
    private val BIN_NORMAL = SpriteRegion(0, 142, 22, 22)
    private val BIN_HOVER = SpriteRegion(22, 142, 22, 22)

    private data class CapabilityEntry(
        val name: String,
        val iconIndex: Int,
        val supported: Boolean = true,
        val description: String = "",
    )

    private val CAPABILITIES = listOf(
        CapabilityEntry("Bulldozer", 0, description = "Quickly break multiple blocks"),
        CapabilityEntry("Replace Mode", 1, description = "Only replace non-air blocks"),
        CapabilityEntry("Force Place", 2, supported = false, description = "Not yet implemented"),
        CapabilityEntry("No Updates", 3, supported = false, description = "Not yet implemented"),
        CapabilityEntry("Tinker", 4, supported = false, description = "Not yet implemented"),
        CapabilityEntry("Infinite Reach", 5, description = "Extended block interaction range"),
        CapabilityEntry("Fast Place", 6, description = "Place blocks at maximum speed"),
        CapabilityEntry("Angel Placement", 7, supported = false, description = "Not yet implemented"),
        CapabilityEntry("No Clip", 8, description = "Phase through blocks while flying"),
        CapabilityEntry("Phantom", 9, description = "Walk over tripwires, pressure plates, and sculk sensors without triggering them"),
    )

    private fun capabilityState(index: Int): Boolean {
        val state = AxionClientState.globalModeState
        return when (index) {
            0 -> state.bulldozerEnabled
            1 -> state.replaceModeEnabled
            5 -> state.infiniteReachEnabled
            6 -> state.fastPlaceEnabled
            8 -> state.noClipEnabled
            9 -> state.phantomEnabled
            else -> false
        }
    }

    private fun capabilitySlotFrame(index: Int, hovered: Boolean, active: Boolean) = SpriteRegion(
        u = if (active) 236 else 216,
        v = if (hovered) 20 else 0,
        w = 20,
        h = 20,
    )

    private fun capabilityIcon(index: Int, active: Boolean) = SpriteRegion(
        u = 16 * index,
        v = if (active) 24 else 40,
        w = 16,
        h = 16,
    )

    private data class SpriteRegion(val u: Int, val v: Int, val w: Int, val h: Int)

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
        val showToolStack = axionSelected || !SavedHotbarController.isOverlayActive(client)
        val expandedTools = axionSelected && AxionModifierKeys.isAltDown(client)

        if (!showToolStack) return

        renderToolStack(
            context = context,
            sideSlot = sideSlot,
            activeSubtool = activeSubtool,
            hovered = if (expandedTools) {
                AxionAltMenuController.hoveredSubtool(client, context.scaledWindowWidth, context.scaledWindowHeight)
            } else {
                null
            },
            selected = axionSelected,
            expanded = expandedTools,
        )

        if (expandedTools) {
            if (AxionDevTestSession.isActive) {
                renderFinishTestingButton(
                    context = context,
                    sideSlot = sideSlot,
                )
            }
            renderMiddleClickToggle(
                context = context,
                sideSlot = sideSlot,
            )
            renderKeepExistingToggle(
                context = context,
                sideSlot = sideSlot,
            )
            renderCopyEntitiesToggle(
                context = context,
                sideSlot = sideSlot,
            )
            renderCopyAirToggle(
                context = context,
                sideSlot = sideSlot,
            )
        }
    }

    private fun renderToolStack(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
        activeSubtool: AxionSubtool,
        hovered: AxionSubtool?,
        selected: Boolean,
        expanded: Boolean,
    ) {
        val entries = AxionHudLayout.stripEntries(sideSlot)
        val visibleEntries = if (expanded) {
            entries
        } else {
            listOf(
                AxionHudLayout.StripEntryBounds(
                    x = sideSlot.x,
                    y = sideSlot.y + 3,
                    width = TOOL_STACK_WIDTH,
                    height = TOOL_STACK_ROW_HEIGHT,
                    subtool = activeSubtool,
                ),
            )
        }
        val backgroundHeight = TOOL_STACK_BASE_HEIGHT + (visibleEntries.size * TOOL_STACK_ROW_HEIGHT)
        val backgroundY = sideSlot.y + 23 - (visibleEntries.size * TOOL_STACK_ROW_HEIGHT)

        drawToolSwapperRegion(
            context = context,
            x = sideSlot.x,
            y = backgroundY,
            u = 0,
            v = 0,
            width = TOOL_STACK_WIDTH,
            height = backgroundHeight,
        )

        visibleEntries.forEach { entry ->
            val toolIndex = AxionHudLayout.TOOL_STACK_ORDER.indexOf(entry.subtool).coerceAtLeast(0)
            drawToolSwapperRegion(
                context = context,
                x = entry.x + 3,
                y = entry.y + 3,
                u = 46 + (TOOL_ICON_SIZE * toolIndex),
                v = 0,
                width = TOOL_ICON_SIZE,
                height = TOOL_ICON_SIZE,
            )
        }

        if (selected) {
            val selectedEntry = visibleEntries.firstOrNull { it.subtool == activeSubtool }
            selectedEntry?.let { entry ->
                drawToolSwapperRegion(
                    context = context,
                    x = sideSlot.x - 1,
                    y = entry.y - 1,
                    u = 22,
                    v = 0,
                    width = TOOL_SELECTED_WIDTH,
                    height = TOOL_SELECTED_HEIGHT,
                )
            }
        }

        if (hovered != null) {
            visibleEntries.firstOrNull { it.subtool == hovered }?.let { entry ->
                context.fill(entry.x + 3, entry.y + 3, entry.x + 19, entry.y + 19, 0x44FFFFFF)
            }
        }
    }

    private fun drawToolSwapperRegion(
        context: DrawContext,
        x: Int,
        y: Int,
        u: Int,
        v: Int,
        width: Int,
        height: Int,
    ) {
        VersionCompatImpl.drawGuiTextureRegion(
            context = context,
            texture = TOOL_SWAPPER_TEXTURE,
            x = x,
            y = y,
            u = u,
            v = v,
            width = width,
            height = height,
            textureWidth = TOOL_SWAPPER_ATLAS_SIZE,
            textureHeight = TOOL_SWAPPER_ATLAS_SIZE,
        )
    }

    private fun drawHotbarSwapperRegion(
        context: DrawContext,
        u: Int,
        v: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        VersionCompatImpl.drawGuiTextureRegion(
            context = context,
            texture = HOTBAR_SWAPPER_TEXTURE,
            x = x,
            y = y,
            u = u,
            v = v,
            width = width,
            height = height,
            textureWidth = ATLAS_SIZE,
            textureHeight = ATLAS_SIZE,
        )
    }

    private fun drawHotbarSwapperRegion(
        context: DrawContext,
        region: SpriteRegion,
        x: Int,
        y: Int,
    ) {
        drawHotbarSwapperRegion(context, region.u, region.v, x, y, region.w, region.h)
    }

    private data class PendingTooltip(
        val textRenderer: net.minecraft.client.font.TextRenderer,
        val lines: List<Pair<String, Int>>,
        val x: Int,
        val y: Int,
    )

    private var pendingTooltip: PendingTooltip? = null

    private fun renderSavedHotbarOverlay(
        context: DrawContext,
        client: MinecraftClient,
    ) {
        val matrices = context.matrices
        pushMatrices(matrices)
        translateMatrices(matrices, 0.0, 0.0, 200.0)
        pendingTooltip = null
        try {
            val page = SavedHotbarController.selectedPage()
            val displayRows = SavedHotbarController.displayHotbarsForSelectedPage(client)
            val rowBounds = AxionHudLayout.savedHotbarRows(context.scaledWindowWidth, context.scaledWindowHeight, page)

            // 9×9 grid background
            val centerX = context.scaledWindowWidth / 2
            drawHotbarSwapperRegion(context, HOTBAR_GRID_BG, centerX - 91, context.scaledWindowHeight - 182)
            renderSavedHotbarActionButtons(context, client, page)
            if (AxionDevTestSession.isActive) {
                renderFinishTestingButton(
                    context,
                    AxionHudLayout.finishTestingSavedHotbarBounds(
                        context.scaledWindowWidth,
                        context.scaledWindowHeight,
                        page,
                    ),
                )
            }

            val hoveredSlot = findHoveredSlot(client, context.scaledWindowWidth, context.scaledWindowHeight, rowBounds)

            rowBounds.zip(displayRows).forEach { (bounds, display) ->
                if (display.selected) {
                    drawHotbarSwapperRegion(context, SEL_HIGHLIGHT, bounds.x - 1, bounds.y - 1)
                }
                renderSavedHotbarItems(context, bounds.x + 1, bounds.y + 1, bounds.index, display.stacks, hoveredSlot)
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
            renderToolboxButton(context, client)
            renderCapabilities(context, client)
            renderBinSlot(context, client)
            renderGrabbedItem(context, client)
        } finally {
            popMatrices(matrices)
            pendingTooltip?.let { (textRenderer, lines, mx, my) ->
                renderTooltipNow(context, textRenderer, lines, mx, my)
            }
            pendingTooltip = null
        }
    }

    private fun renderSavedHotbarActionButtons(
        context: DrawContext,
        client: MinecraftClient,
        page: Int,
    ) {
        val player = client.player ?: return
        val currentMode = when {
            player.isSpectator -> SavedHotbarMenuAction.SPECTATOR
            AxionToolSelectionController.isCreativeModeAllowed() -> SavedHotbarMenuAction.CREATIVE
            else -> SavedHotbarMenuAction.SURVIVAL
        }

        AxionHudLayout.savedHotbarActionButtons(
            context.scaledWindowWidth,
            context.scaledWindowHeight,
            page,
        ).filterNot { bounds ->
            AxionDevTestSession.isActive && bounds.action == SavedHotbarMenuAction.CREATE_DISPLAY_ENTITY
        }.forEach { bounds ->
            VanillaHudButtonStore.render(
                context = context,
                key = VanillaHudButtonStore.actionKey(bounds.action),
                label = bounds.action.label,
                x = bounds.x,
                y = bounds.y,
                width = bounds.width,
                height = bounds.height,
                enabled = bounds.enabled,
                selected = bounds.action == currentMode,
            )
        }
    }

    private fun renderTooltipNow(
        context: DrawContext,
        textRenderer: net.minecraft.client.font.TextRenderer,
        lines: List<Pair<String, Int>>,
        x: Int,
        y: Int,
    ) {
        if (lines.isEmpty()) return
        val lineWidths = lines.map { textRenderer.getWidth(it.first) }
        val maxWidth = lineWidths.max()
        val padding = 4
        val bgX = x + 12
        val bgY = y - 12
        val bgW = maxWidth + padding * 2
        val bgH = lines.size * (textRenderer.fontHeight + 2) + padding
        val screenWidth = context.scaledWindowWidth
        val screenHeight = context.scaledWindowHeight
        val clampedBgX = bgX.coerceIn(0, screenWidth - bgW)
        val clampedBgY = bgY.coerceIn(0, screenHeight - bgH)
        context.fill(clampedBgX, clampedBgY, clampedBgX + bgW, clampedBgY + bgH, 0xF0100010.toInt())
        context.drawStrokedRectangleCompat(clampedBgX, clampedBgY, bgW, bgH, 0x505000FF)
        lines.forEachIndexed { i, (text, color) ->
            context.drawTextWithShadow(textRenderer, text, clampedBgX + padding, clampedBgY + padding + i * (textRenderer.fontHeight + 2), opaqueTextColor(color))
        }
    }

    private fun opaqueTextColor(color: Int): Int {
        return if ((color and OPAQUE_ALPHA) == 0) {
            color or OPAQUE_ALPHA
        } else {
            color
        }
    }

    private fun renderTooltip(
        context: DrawContext,
        textRenderer: net.minecraft.client.font.TextRenderer,
        lines: List<Pair<String, Int>>,
        x: Int,
        y: Int,
    ) {
        pendingTooltip = PendingTooltip(textRenderer, lines, x, y)
    }

    private fun pushMatrices(matrices: Any) {
        invokeNoArg(matrices, "push") ?: invokeNoArg(matrices, "pushMatrix")
    }

    private fun popMatrices(matrices: Any) {
        invokeNoArg(matrices, "pop") ?: invokeNoArg(matrices, "popMatrix")
    }

    private fun translateMatrices(matrices: Any, x: Double, y: Double, z: Double) {
        matrices.javaClass.methods.firstOrNull {
            it.name == "translate" && it.parameterCount == 3 &&
                it.parameterTypes[0] == Double::class.javaPrimitiveType &&
                it.parameterTypes[1] == Double::class.javaPrimitiveType &&
                it.parameterTypes[2] == Double::class.javaPrimitiveType
        }?.invoke(matrices, x, y, z) ?: matrices.javaClass.methods.firstOrNull {
            it.name == "translate" && it.parameterCount == 2 &&
                it.parameterTypes[0] == Float::class.javaPrimitiveType &&
                it.parameterTypes[1] == Float::class.javaPrimitiveType
        }?.invoke(matrices, x.toFloat(), y.toFloat())
    }

    private fun invokeNoArg(target: Any, name: String): Any? {
        return target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(target)
    }

    /** Calls drawStackOverlay if it exists (1.21.4+), otherwise falls back to drawItemInSlot (1.21.0-1.21.1). */
    private fun drawStackOverlayReflective(context: DrawContext, textRenderer: net.minecraft.client.font.TextRenderer, stack: ItemStack, x: Int, y: Int) {
        context.javaClass.methods.firstOrNull { method ->
            method.name == "drawStackOverlay" && method.parameterCount == 4
        }?.invoke(context, textRenderer, stack, x, y) ?: run {
            context.javaClass.methods.firstOrNull { method ->
                method.name == "drawItemInSlot" && method.parameterCount == 4
            }?.invoke(context, textRenderer, stack, x, y)
        }
    }

    private fun renderFlyingSpeedSlider(
        context: DrawContext,
        client: MinecraftClient,
        page: Int,
    ) {
        if (client.player?.abilities?.allowFlying != true) return

        val bounds = AxionHudLayout.flyingSpeedSliderBounds(context.scaledWindowWidth, context.scaledWindowHeight, page)
        val multiplier = AxionClientState.flySpeedMultiplier

        val plusHovered = AxionAltMenuController.isHoveringFlyingSpeedPlusButton(client, context.scaledWindowWidth, context.scaledWindowHeight)
        val minusHovered = AxionAltMenuController.isHoveringFlyingSpeedMinusButton(client, context.scaledWindowWidth, context.scaledWindowHeight)

        val plus = bounds.plusButton
        drawHotbarSwapperRegion(context, if (plusHovered) FLY_PLUS_HOVER else FLY_PLUS, plus.x, plus.y)

        val percentageText = "${(multiplier * 100).toInt()}%"
        val labelY = plus.y - client.textRenderer.fontHeight - 2
        context.drawCenteredTextWithShadow(client.textRenderer, percentageText, plus.x + plus.width / 2, labelY, TEXT_IDLE)

        val track = bounds.track
        val flyAmount = sqrt(((multiplier - 1.0f) / 8.99f).coerceIn(0f, 1f))
        val filledHeight = ((1.0f - flyAmount) * track.height).toInt().coerceIn(0, track.height)
        val emptyHeight = track.height - filledHeight

        if (filledHeight > 0) {
            drawHotbarSwapperRegion(context, FLY_BAR_FILLED.u, FLY_BAR_FILLED.v, track.x, track.y, track.width, filledHeight)
        }
        if (emptyHeight > 0) {
            drawHotbarSwapperRegion(context, FLY_BAR_EMPTY.u, FLY_BAR_EMPTY.v + filledHeight, track.x, track.y + filledHeight, track.width, emptyHeight)
        }

        val minus = bounds.minusButton
        drawHotbarSwapperRegion(context, if (minusHovered) FLY_MINUS_HOVER else FLY_MINUS, minus.x, minus.y)
    }

    private fun renderToolboxButton(
        context: DrawContext,
        client: MinecraftClient,
    ) {
        val bounds = AxionHudLayout.toolboxSlotBounds(client, context.scaledWindowWidth, context.scaledWindowHeight)
        val hovered = AxionAltMenuController.isHoveringToolboxButton(client, context.scaledWindowWidth, context.scaledWindowHeight)
        val centerOffset = (bounds.size - 20) / 2
        drawHotbarSwapperRegion(context, if (hovered) TOOLBOX_SLOT_HOVER else TOOLBOX_SLOT, bounds.x + centerOffset, bounds.y + centerOffset)
        drawHotbarSwapperRegion(context, WRENCH, bounds.x + centerOffset + 2, bounds.y + centerOffset + 2)
    }

    private fun renderBinSlot(
        context: DrawContext,
        client: MinecraftClient,
    ) {
        val centerX = context.scaledWindowWidth / 2
        val mainX = when (client.options.mainArm.value) {
            Arm.LEFT -> centerX - 109
            Arm.RIGHT -> centerX + 109
        }
        val screenHeight = context.scaledWindowHeight
        val mouseX = VersionCompatImpl.getScaledMouseX(client).toInt()
        val mouseY = VersionCompatImpl.getScaledMouseY(client).toInt()
        val hovered = mouseX >= mainX - 11 && mouseX < mainX + 13 && mouseY >= screenHeight - 22 && mouseY < screenHeight
        val hasGrabbed = !AxionAltMenuController.grabbedStack.isEmpty
        drawHotbarSwapperRegion(context, if (hovered && hasGrabbed) BIN_HOVER else BIN_NORMAL, mainX - 11, screenHeight - 22)
        if (hovered) {
            val lines = listOf(
                "Delete" to 0xFFFFFF,
                "Drag items here or shift-click to clear page" to 0x808080,
            )
            renderTooltip(context, client.textRenderer, lines, mouseX, mouseY)
        }
    }

    private fun renderGrabbedItem(
        context: DrawContext,
        client: MinecraftClient,
    ) {
        val stack = AxionAltMenuController.grabbedStack
        if (!stack.isEmpty) {
            val mouseX = VersionCompatImpl.getScaledMouseX(client).toInt()
            val mouseY = VersionCompatImpl.getScaledMouseY(client).toInt()
            context.drawItem(stack, mouseX - 8, mouseY - 8)
            drawStackOverlayReflective(context, client.textRenderer, stack, mouseX - 8, mouseY - 8)
        }
    }

    private fun renderCapabilities(
        context: DrawContext,
        client: MinecraftClient,
    ) {
        val centerX = context.scaledWindowWidth / 2
        val offX = when (client.options.mainArm.value) {
            Arm.LEFT -> centerX + 107
            Arm.RIGHT -> centerX - 107
        }
        val screenHeight = context.scaledWindowHeight
        val mouseX = VersionCompatImpl.getScaledMouseX(client).toInt()
        val mouseY = VersionCompatImpl.getScaledMouseY(client).toInt()

        CAPABILITIES.forEachIndexed { index, cap ->
            val y = screenHeight - 44 - 22 * index
            val hovered = mouseX >= offX - 10 && mouseX < offX + 10 && mouseY >= y && mouseY < y + 20
            val active = capabilityState(index)
            drawHotbarSwapperRegion(context, capabilitySlotFrame(index, hovered, active), offX - 10, y)
            drawHotbarSwapperRegion(context, capabilityIcon(cap.iconIndex, active), offX - 8, y + 2)
            if (hovered) {
                val tooltip = if (cap.supported)
                    listOf(cap.name to 0xFFFFFF, cap.description to 0x808080)
                else
                    listOf(cap.name to 0x808080, cap.description to 0x606060)
                renderTooltip(context, client.textRenderer, tooltip, mouseX, mouseY)
            }
        }
    }


    private fun findHoveredSlot(
        client: MinecraftClient,
        screenWidth: Int,
        screenHeight: Int,
        rowBounds: List<AxionHudLayout.SavedHotbarRowBounds>,
    ): Pair<Int, Int>? {
        val mouseX = VersionCompatImpl.getScaledMouseX(client).toInt()
        val mouseY = VersionCompatImpl.getScaledMouseY(client).toInt()
        for (row in rowBounds) {
            val startX = row.x + 1
            val startY = row.y + 1
            for (slot in 0 until 9) {
                val slotX = startX + slot * 20
                if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= startY && mouseY < startY + 18) {
                    return Pair(row.index, slot)
                }
            }
        }
        return null
    }

    private fun renderSlotHover(context: DrawContext, x: Int, y: Int) {
        context.fill(x, y, x + 16, y + 16, 0x80FFFFFF.toInt())
    }

    private fun renderSavedHotbarItems(
        context: DrawContext,
        startX: Int,
        startY: Int,
        rowIndex: Int,
        stacks: List<ItemStack>,
        hoveredSlot: Pair<Int, Int>?,
    ) {
        stacks.take(9).forEachIndexed { index, stack ->
            val slotX = startX + (index * 20)
            val hov = hoveredSlot
            val showHover = hov != null && hov.first == rowIndex && hov.second == index &&
                (!AxionAltMenuController.grabbedStack.isEmpty || !stack.isEmpty)
            if (showHover) {
                renderSlotHover(context, slotX + 2, startY + 2)
            }
            if (!stack.isEmpty) {
                context.drawItem(stack, slotX + 2, startY + 2)
                drawStackOverlayReflective(context, MinecraftClient.getInstance().textRenderer, stack, slotX + 2, startY + 2)
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

    private fun renderMiddleClickToggle(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
    ) {
        val bounds = AxionHudLayout.middleClickToggleBounds(sideSlot)
        val enabled = AxionClientState.middleClickMagicSelectEnabled
        val label = if (enabled) "MMB: Magic Select" else "MMB: Extend Face"

        VanillaHudButtonStore.render(
            context,
            VanillaHudButtonStore.MIDDLE_CLICK,
            label,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            selected = enabled,
        )
    }

    private fun renderFinishTestingButton(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
    ) {
        renderFinishTestingButton(context, AxionHudLayout.finishTestingBounds(sideSlot))
    }

    private fun renderFinishTestingButton(
        context: DrawContext,
        bounds: AxionHudLayout.ToggleButtonBounds,
    ) {
        VanillaHudButtonStore.render(
            context,
            VanillaHudButtonStore.FINISH_TESTING,
            "Finish testing",
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
        )
    }

    private fun renderKeepExistingToggle(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
    ) {
        val bounds = AxionHudLayout.keepExistingToggleBounds(sideSlot)
        val enabled = AxionClientState.keepExistingEnabled
        val label = if (enabled) "Keep Existing: ON" else "Keep Existing: OFF"

        VanillaHudButtonStore.render(
            context,
            VanillaHudButtonStore.KEEP_EXISTING,
            label,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            selected = enabled,
        )
    }

    private fun renderCopyEntitiesToggle(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
    ) {
        val bounds = AxionHudLayout.copyEntitiesToggleBounds(sideSlot)
        val enabled = AxionClientState.copyEntitiesEnabled
        val label = if (enabled) "Copy Entities: ON" else "Copy Entities: OFF"

        VanillaHudButtonStore.render(
            context,
            VanillaHudButtonStore.COPY_ENTITIES,
            label,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            selected = enabled,
        )
    }

    private fun renderCopyAirToggle(
        context: DrawContext,
        sideSlot: AxionHudLayout.SlotBounds,
    ) {
        val bounds = AxionHudLayout.copyAirToggleBounds(sideSlot)
        val enabled = AxionClientState.copyAirEnabled
        val label = if (enabled) "Copy Air: ON" else "Copy Air: OFF"

        VanillaHudButtonStore.render(
            context,
            VanillaHudButtonStore.COPY_AIR,
            label,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            selected = enabled,
        )
    }
}

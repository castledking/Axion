package axion.client.hotbar

import axion.common.model.AxionSubtool
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Arm

object AxionHudLayout {
    private const val HOTBAR_HALF_WIDTH: Int = 91
    private const val SLOT_SIZE: Int = 24
    private const val STRIP_GAP: Int = 4
    private const val SAVED_HOTBAR_WIDTH: Int = 182
    private const val SAVED_HOTBAR_HEIGHT: Int = 20
    private const val SAVED_HOTBAR_PAGE_BUTTON_WIDTH: Int = 12
    private const val SAVED_HOTBAR_PAGE_BUTTON_HEIGHT: Int = 12
    private const val FLY_BUTTON_WIDTH: Int = 16
    private const val FLY_BUTTON_HEIGHT: Int = 14
    private const val FLY_TRACK_WIDTH: Int = 16
    private const val ACTION_BUTTON_HEIGHT: Int = 18
    private const val ACTION_BUTTON_GAP: Int = 2
    const val STRIP_ENTRY_HEIGHT: Int = 18
    const val STRIP_ENTRY_WIDTH: Int = 42
    const val STRIP_ENTRY_GAP: Int = 2
    private const val TOOL_STACK_ENTRY_WIDTH: Int = 22
    private const val TOOL_STACK_ENTRY_HEIGHT: Int = 20

    val TOOL_STACK_ORDER: List<AxionSubtool> = AxionSubtool.toolbarOrder

    data class SlotBounds(
        val x: Int,
        val y: Int,
        val size: Int,
    )

    data class StripEntryBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val subtool: AxionSubtool,
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
        }
    }

    data class ToggleButtonBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
        }
    }

    data class SavedHotbarRowBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val index: Int,
    )

    data class SavedHotbarPageButtonBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val direction: Int,
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
        }
    }

    data class SavedHotbarActionButtonBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val action: SavedHotbarMenuAction,
        val enabled: Boolean,
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
        }
    }

    fun sideSlot(client: MinecraftClient, screenWidth: Int, screenHeight: Int): SlotBounds {
        val hotbarLeft = (screenWidth / 2) - HOTBAR_HALF_WIDTH
        val slotX = when (client.options.mainArm.value) {
            Arm.LEFT -> hotbarLeft - SLOT_SIZE - STRIP_GAP
            Arm.RIGHT -> hotbarLeft + (HOTBAR_HALF_WIDTH * 2) + STRIP_GAP
        }

        return SlotBounds(
            x = slotX,
            y = screenHeight - SLOT_SIZE - 1,
            size = SLOT_SIZE,
        )
    }

    fun stripOrigin(sideSlot: SlotBounds): Pair<Int, Int> {
        return sideSlot.x to (sideSlot.y - STRIP_GAP)
    }

    fun stripEntries(sideSlot: SlotBounds): List<StripEntryBounds> {
        return TOOL_STACK_ORDER.mapIndexed { index, subtool ->
            val boxY = sideSlot.y + 3 - (index * TOOL_STACK_ENTRY_HEIGHT)
            StripEntryBounds(
                x = sideSlot.x,
                y = boxY,
                width = TOOL_STACK_ENTRY_WIDTH,
                height = TOOL_STACK_ENTRY_HEIGHT,
                subtool = subtool,
            )
        }
    }

    fun subtoolAt(sideSlot: SlotBounds, mouseX: Double, mouseY: Double): AxionSubtool? {
        return stripEntries(sideSlot).firstOrNull { it.contains(mouseX, mouseY) }?.subtool
    }

    private fun leftColumnToggleBounds(sideSlot: SlotBounds, rowFromBottom: Int): ToggleButtonBounds {
        val entries = stripEntries(sideSlot)
        val width = 136
        val height = STRIP_ENTRY_HEIGHT
        val x = entries.firstOrNull()?.x?.minus(width + STRIP_GAP) ?: (sideSlot.x - width - STRIP_GAP)
        val bottomY = sideSlot.y - height - STRIP_GAP
        val y = bottomY - ((height + STRIP_ENTRY_GAP) * rowFromBottom)
        return ToggleButtonBounds(x = x, y = y, width = width, height = height)
    }

    fun middleClickToggleBounds(sideSlot: SlotBounds): ToggleButtonBounds = leftColumnToggleBounds(sideSlot, rowFromBottom = 3)

    fun keepExistingToggleBounds(sideSlot: SlotBounds): ToggleButtonBounds = leftColumnToggleBounds(sideSlot, rowFromBottom = 2)

    fun copyEntitiesToggleBounds(sideSlot: SlotBounds): ToggleButtonBounds = leftColumnToggleBounds(sideSlot, rowFromBottom = 1)

    fun copyAirToggleBounds(sideSlot: SlotBounds): ToggleButtonBounds = leftColumnToggleBounds(sideSlot, rowFromBottom = 0)

    fun savedHotbarRows(screenWidth: Int, screenHeight: Int, page: Int): List<SavedHotbarRowBounds> {
        val x = (screenWidth / 2) - HOTBAR_HALF_WIDTH
        val bottomY = screenHeight - 22
        val pageStart = page * SavedHotbarController.PAGE_SIZE
        return (0 until SavedHotbarController.PAGE_SIZE).map { row ->
            SavedHotbarRowBounds(
                x = x,
                y = bottomY - (row * SAVED_HOTBAR_HEIGHT),
                width = SAVED_HOTBAR_WIDTH,
                height = SAVED_HOTBAR_HEIGHT,
                index = pageStart + row,
            )
        }
    }

    fun savedHotbarActionButtons(screenWidth: Int, screenHeight: Int, page: Int): List<SavedHotbarActionButtonBounds> {
        val topRow = savedHotbarRows(screenWidth, screenHeight, page).last()
        val modeY = topRow.y - ACTION_BUTTON_HEIGHT
        val editY = modeY - ACTION_BUTTON_GAP - ACTION_BUTTON_HEIGHT
        val displayY = editY - ACTION_BUTTON_GAP - ACTION_BUTTON_HEIGHT
        val modeWidths = intArrayOf(59, 59, 60)
        val modeActions = listOf(
            SavedHotbarMenuAction.SURVIVAL,
            SavedHotbarMenuAction.SPECTATOR,
            SavedHotbarMenuAction.CREATIVE,
        )
        var modeX = topRow.x

        return buildList {
            add(
                SavedHotbarActionButtonBounds(
                    x = topRow.x,
                    y = displayY,
                    width = topRow.width,
                    height = ACTION_BUTTON_HEIGHT,
                    action = SavedHotbarMenuAction.CREATE_DISPLAY_ENTITY,
                    enabled = false,
                ),
            )
            add(
                SavedHotbarActionButtonBounds(
                    x = topRow.x,
                    y = editY,
                    width = topRow.width,
                    height = ACTION_BUTTON_HEIGHT,
                    action = SavedHotbarMenuAction.EDIT_BLOCK_ATTRIBUTES,
                    enabled = false,
                ),
            )
            modeActions.forEachIndexed { index, action ->
                add(
                    SavedHotbarActionButtonBounds(
                        x = modeX,
                        y = modeY,
                        width = modeWidths[index],
                        height = ACTION_BUTTON_HEIGHT,
                        action = action,
                        enabled = true,
                    ),
                )
                modeX += modeWidths[index] + ACTION_BUTTON_GAP
            }
        }
    }

    fun savedHotbarPageButtons(screenWidth: Int, screenHeight: Int, page: Int): List<SavedHotbarPageButtonBounds> {
        val topRow = savedHotbarRows(screenWidth, screenHeight, page).last()
        val buttonX = topRow.x + topRow.width + 12
        val topButtonY = topRow.y + 18
        return listOf(
            SavedHotbarPageButtonBounds(
                x = buttonX,
                y = topButtonY,
                width = SAVED_HOTBAR_PAGE_BUTTON_WIDTH,
                height = SAVED_HOTBAR_PAGE_BUTTON_HEIGHT,
                direction = 1,
            ),
            SavedHotbarPageButtonBounds(
                x = buttonX + SAVED_HOTBAR_PAGE_BUTTON_WIDTH + 4,
                y = topButtonY,
                width = SAVED_HOTBAR_PAGE_BUTTON_WIDTH,
                height = SAVED_HOTBAR_PAGE_BUTTON_HEIGHT,
                direction = -1,
            ),
        )
    }

    data class FlyingSpeedSliderBounds(
        val plusButton: ToggleButtonBounds,
        val track: ToggleButtonBounds,
        val minusButton: ToggleButtonBounds,
    ) {
        fun trackValueFromY(mouseY: Double): Float {
            val normalized = (1.0 - (mouseY - track.y) / track.height).coerceIn(0.0, 1.0)
            return 1.0f + ((normalized * normalized) * 8.99f).toFloat()
        }
    }

    fun flyingSpeedSliderBounds(screenWidth: Int, screenHeight: Int, page: Int): FlyingSpeedSliderBounds {
        val pageButtons = savedHotbarPageButtons(screenWidth, screenHeight, page)
        val buttonX = pageButtons.firstOrNull()?.x ?: ((screenWidth / 2) - HOTBAR_HALF_WIDTH + SAVED_HOTBAR_WIDTH + 12)
        val downButton = pageButtons.firstOrNull { it.direction < 0 }
        val buttonBottom = downButton?.let { it.y + it.height } ?: (screenHeight - 22 + 18 + 12)

        val btnWidth = FLY_BUTTON_WIDTH
        val btnHeight = FLY_BUTTON_HEIGHT
        val centerX = buttonX + (SAVED_HOTBAR_PAGE_BUTTON_WIDTH / 2)

        val fontHeight = MinecraftClient.getInstance().textRenderer.fontHeight
        val plusY = buttonBottom + fontHeight + 6
        val trackY = plusY + btnHeight

        val sideSlot = sideSlot(MinecraftClient.getInstance(), screenWidth, screenHeight)
        val axSlotTop = sideSlot.y
        val toolboxReserve = sideSlot.size + 2 + 6
        val minusGap = 0

        val availableForTrack = axSlotTop - toolboxReserve - btnHeight - minusGap - trackY
        val trackHeight = availableForTrack.coerceIn(24, 64)

        val minusY = trackY + trackHeight + minusGap

        val trackX = centerX - (FLY_TRACK_WIDTH / 2)
        val btnX = centerX - (btnWidth / 2)

        return FlyingSpeedSliderBounds(
            plusButton = ToggleButtonBounds(x = btnX, y = plusY, width = btnWidth, height = btnHeight),
            track = ToggleButtonBounds(x = trackX, y = trackY, width = FLY_TRACK_WIDTH, height = trackHeight),
            minusButton = ToggleButtonBounds(x = btnX, y = minusY, width = btnWidth, height = btnHeight),
        )
    }

    fun toolboxSlotBounds(client: MinecraftClient, screenWidth: Int, screenHeight: Int): SlotBounds {
        val sideSlot = sideSlot(client, screenWidth, screenHeight)
        return SlotBounds(
            x = sideSlot.x,
            y = sideSlot.y - 2 - sideSlot.size,  // 20px tall (same as slot size), 2px gap above
            size = sideSlot.size
        )
    }
}

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
    private const val SAVED_HOTBAR_GAP: Int = 1
    private const val SAVED_HOTBAR_PAGE_BUTTON_WIDTH: Int = 12
    private const val SAVED_HOTBAR_PAGE_BUTTON_HEIGHT: Int = 12
    const val STRIP_ENTRY_HEIGHT: Int = 18
    const val STRIP_ENTRY_WIDTH: Int = 42
    const val STRIP_ENTRY_GAP: Int = 2

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
        val (originX, originBottom) = stripOrigin(sideSlot)
        val stripX = originX - (STRIP_ENTRY_WIDTH - sideSlot.size)
        return AxionSubtool.entries.mapIndexed { index, subtool ->
            val boxY = originBottom - ((index + 1) * (STRIP_ENTRY_HEIGHT + STRIP_ENTRY_GAP))
            StripEntryBounds(
                x = stripX,
                y = boxY,
                width = STRIP_ENTRY_WIDTH,
                height = STRIP_ENTRY_HEIGHT,
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
        // Reuse the vanilla hotbar row as the bottom visible saved hotbar entry.
        val bottomY = screenHeight - 22
        val pageStart = page * SavedHotbarController.PAGE_SIZE
        return (0 until SavedHotbarController.PAGE_SIZE).map { row ->
            SavedHotbarRowBounds(
                x = x,
                y = bottomY - (row * (SAVED_HOTBAR_HEIGHT + SAVED_HOTBAR_GAP)),
                width = SAVED_HOTBAR_WIDTH,
                height = SAVED_HOTBAR_HEIGHT,
                index = pageStart + row,
            )
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
}

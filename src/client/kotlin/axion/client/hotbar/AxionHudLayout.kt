package axion.client.hotbar

import axion.common.model.AxionSubtool
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Arm

object AxionHudLayout {
    private const val HOTBAR_HALF_WIDTH: Int = 91
    private const val SLOT_SIZE: Int = 24
    private const val STRIP_GAP: Int = 4
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

    private fun leftColumnToggleBounds(sideSlot: SlotBounds, row: Int): ToggleButtonBounds {
        val entries = stripEntries(sideSlot)
        val width = 136
        val height = STRIP_ENTRY_HEIGHT
        val x = entries.firstOrNull()?.x?.minus(width + STRIP_GAP) ?: (sideSlot.x - width - STRIP_GAP)
        val baseY = entries.lastOrNull()?.y ?: (sideSlot.y - height - STRIP_GAP)
        val y = baseY - ((height + STRIP_ENTRY_GAP) * row)
        return ToggleButtonBounds(x = x, y = y, width = width, height = height)
    }

    fun middleClickToggleBounds(sideSlot: SlotBounds): ToggleButtonBounds = leftColumnToggleBounds(sideSlot, row = 1)

    fun keepExistingToggleBounds(sideSlot: SlotBounds): ToggleButtonBounds = leftColumnToggleBounds(sideSlot, row = 0)
}

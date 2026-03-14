package axion.client.hotbar

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Arm

object AxionHudLayout {
    private const val HOTBAR_HALF_WIDTH: Int = 91
    private const val SLOT_SIZE: Int = 24
    private const val STRIP_GAP: Int = 4

    data class SlotBounds(
        val x: Int,
        val y: Int,
        val size: Int,
    )

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
}

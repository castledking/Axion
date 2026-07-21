package axion.client.hotbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedHotbarMenuLayoutTest {
    @Test
    fun fiveActionButtonsSitDirectlyAboveTheSavedHotbarGrid() {
        val screenWidth = 540
        val screenHeight = 360
        val topGridRow = AxionHudLayout.savedHotbarRows(screenWidth, screenHeight, page = 0).last()
        val buttons = AxionHudLayout.savedHotbarActionButtons(screenWidth, screenHeight, page = 0)

        assertEquals(
            listOf(
                SavedHotbarMenuAction.CREATE_DISPLAY_ENTITY,
                SavedHotbarMenuAction.EDIT_BLOCK_ATTRIBUTES,
                SavedHotbarMenuAction.SURVIVAL,
                SavedHotbarMenuAction.SPECTATOR,
                SavedHotbarMenuAction.CREATIVE,
            ),
            buttons.map { it.action },
        )
        assertEquals(topGridRow.y, buttons.takeLast(3).maxOf { it.y + it.height })
        assertTrue(buttons.all { it.x >= topGridRow.x && it.x + it.width <= topGridRow.x + topGridRow.width })
    }

    @Test
    fun displayEntityActionsAreDisabledPlaceholdersAndGameModesAreEnabled() {
        val buttons = AxionHudLayout.savedHotbarActionButtons(540, 360, page = 0)

        assertFalse(buttons.first { it.action == SavedHotbarMenuAction.CREATE_DISPLAY_ENTITY }.enabled)
        assertFalse(buttons.first { it.action == SavedHotbarMenuAction.EDIT_BLOCK_ATTRIBUTES }.enabled)
        assertTrue(buttons.first { it.action == SavedHotbarMenuAction.SURVIVAL }.enabled)
        assertTrue(buttons.first { it.action == SavedHotbarMenuAction.SPECTATOR }.enabled)
        assertTrue(buttons.first { it.action == SavedHotbarMenuAction.CREATIVE }.enabled)
    }

    @Test
    fun gameModeRowSpansTheSameWidthAsTheSavedHotbar() {
        val screenWidth = 540
        val screenHeight = 360
        val grid = AxionHudLayout.savedHotbarRows(screenWidth, screenHeight, page = 0).last()
        val modeButtons = AxionHudLayout.savedHotbarActionButtons(screenWidth, screenHeight, page = 0).takeLast(3)

        assertEquals(grid.x, modeButtons.first().x)
        assertEquals(grid.x + grid.width, modeButtons.last().x + modeButtons.last().width)
        assertTrue(modeButtons.zipWithNext().all { (left, right) -> left.x + left.width < right.x })
    }

    @Test
    fun actionPanelFitsMinecraftMinimumScaledHeight() {
        val buttons = AxionHudLayout.savedHotbarActionButtons(screenWidth = 426, screenHeight = 240, page = 0)

        assertEquals(0, buttons.minOf { it.y })
    }
}

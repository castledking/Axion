package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.input.AxionModifierKeys
import axion.client.tool.AxionToolSelectionController
import axion.common.model.AxionSubtool
import net.minecraft.client.MinecraftClient
import org.lwjgl.glfw.GLFW

object AxionAltMenuController {
    private var cursorUnlockedByAxion: Boolean = false

    fun isActive(client: MinecraftClient): Boolean {
        return client.currentScreen == null &&
            AxionToolSelectionController.isAxionSelected() &&
            AxionModifierKeys.isAltDown(client)
    }

    private fun isAnyAltOverlayActive(client: MinecraftClient): Boolean {
        return isActive(client) || SavedHotbarController.isOverlayActive(client)
    }

    fun onEndTick(client: MinecraftClient) {
        if (client.currentScreen != null) {
            return
        }

        val active = isAnyAltOverlayActive(client)
        if (active) {
            if (client.mouse.isCursorLocked) {
                client.mouse.unlockCursor()
                cursorUnlockedByAxion = true
            }
            return
        }

        if (cursorUnlockedByAxion && !client.mouse.isCursorLocked) {
            client.mouse.lockCursor()
        }
        cursorUnlockedByAxion = false
    }

    fun hoveredSubtool(client: MinecraftClient, screenWidth: Int, screenHeight: Int): AxionSubtool? {
        if (!isActive(client)) {
            return null
        }

        val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
        return AxionHudLayout.subtoolAt(
            sideSlot = sideSlot,
            mouseX = client.mouse.getScaledX(client.window),
            mouseY = client.mouse.getScaledY(client.window),
        )
    }

    fun isHoveringMiddleClickToggle(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive(client)) {
            return false
        }

        val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
        val bounds = AxionHudLayout.middleClickToggleBounds(sideSlot)
        return bounds.contains(
            client.mouse.getScaledX(client.window),
            client.mouse.getScaledY(client.window),
        )
    }

    fun isHoveringKeepExistingToggle(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive(client)) {
            return false
        }

        val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
        val bounds = AxionHudLayout.keepExistingToggleBounds(sideSlot)
        return bounds.contains(
            client.mouse.getScaledX(client.window),
            client.mouse.getScaledY(client.window),
        )
    }

    fun isHoveringCopyEntitiesToggle(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive(client)) {
            return false
        }

        val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
        val bounds = AxionHudLayout.copyEntitiesToggleBounds(sideSlot)
        return bounds.contains(
            client.mouse.getScaledX(client.window),
            client.mouse.getScaledY(client.window),
        )
    }

    fun isHoveringCopyAirToggle(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive(client)) {
            return false
        }

        val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
        val bounds = AxionHudLayout.copyAirToggleBounds(sideSlot)
        return bounds.contains(
            client.mouse.getScaledX(client.window),
            client.mouse.getScaledY(client.window),
        )
    }

    fun hoveringSavedHotbarPageButton(
        client: MinecraftClient,
        screenWidth: Int,
        screenHeight: Int,
    ): AxionHudLayout.SavedHotbarPageButtonBounds? {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return null
        }

        return AxionHudLayout.savedHotbarPageButtons(screenWidth, screenHeight, SavedHotbarController.selectedPage())
            .firstOrNull { button ->
                button.contains(
                    client.mouse.getScaledX(client.window),
                    client.mouse.getScaledY(client.window),
                )
            }
    }

    fun hoveringSavedHotbarRow(
        client: MinecraftClient,
        screenWidth: Int,
        screenHeight: Int,
    ): AxionHudLayout.SavedHotbarRowBounds? {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return null
        }

        return AxionHudLayout.savedHotbarRows(screenWidth, screenHeight, SavedHotbarController.selectedPage())
            .firstOrNull { row ->
                client.mouse.getScaledX(client.window) >= row.x &&
                    client.mouse.getScaledX(client.window) < row.x + row.width &&
                    client.mouse.getScaledY(client.window) >= row.y &&
                    client.mouse.getScaledY(client.window) < row.y + row.height
            }
    }

    fun handleMouseButton(client: MinecraftClient, button: Int, action: Int): Boolean {
        if (SavedHotbarController.isOverlayActive(client)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                hoveringSavedHotbarPageButton(
                    client,
                    client.window.scaledWidth,
                    client.window.scaledHeight,
                )?.let { buttonBounds ->
                    SavedHotbarController.changePage(buttonBounds.direction)
                    return true
                }
                hoveringSavedHotbarRow(
                    client,
                    client.window.scaledWidth,
                    client.window.scaledHeight,
                )?.let { rowBounds ->
                    SavedHotbarController.selectHotbar(rowBounds.index)
                }
            }
            return true
        }

        if (!isActive(client)) {
            return false
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            if (isHoveringMiddleClickToggle(client, client.window.scaledWidth, client.window.scaledHeight)) {
                AxionClientState.updateMiddleClickMagicSelect(!AxionClientState.middleClickMagicSelectEnabled)
                return true
            }
            if (isHoveringKeepExistingToggle(client, client.window.scaledWidth, client.window.scaledHeight)) {
                AxionClientState.updateKeepExisting(!AxionClientState.keepExistingEnabled)
                return true
            }
            if (isHoveringCopyEntitiesToggle(client, client.window.scaledWidth, client.window.scaledHeight)) {
                AxionClientState.updateCopyEntities(!AxionClientState.copyEntitiesEnabled)
                return true
            }
            if (isHoveringCopyAirToggle(client, client.window.scaledWidth, client.window.scaledHeight)) {
                AxionClientState.updateCopyAir(!AxionClientState.copyAirEnabled)
                return true
            }
            hoveredSubtool(
                client = client,
                screenWidth = client.window.scaledWidth,
                screenHeight = client.window.scaledHeight,
            )?.let(AxionToolSelectionController::selectSubtool)
        }
        return true
    }
}

package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.compat.VersionCompatImpl
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

        // Handle continuous slider dragging
        if (isDraggingSlider) {
            handleFlyingSpeedSliderDrag(client, client.window.scaledWidth, client.window.scaledHeight)
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
            mouseX = VersionCompatImpl.getScaledMouseX(client),
            mouseY = VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun isHoveringMiddleClickToggle(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive(client)) {
            return false
        }

        val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
        val bounds = AxionHudLayout.middleClickToggleBounds(sideSlot)
        return bounds.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun isHoveringKeepExistingToggle(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive(client)) {
            return false
        }

        val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
        val bounds = AxionHudLayout.keepExistingToggleBounds(sideSlot)
        return bounds.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun isHoveringCopyEntitiesToggle(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive(client)) {
            return false
        }

        val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
        val bounds = AxionHudLayout.copyEntitiesToggleBounds(sideSlot)
        return bounds.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun isHoveringCopyAirToggle(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive(client)) {
            return false
        }

        val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
        val bounds = AxionHudLayout.copyAirToggleBounds(sideSlot)
        return bounds.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
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
                    VersionCompatImpl.getScaledMouseX(client),
                    VersionCompatImpl.getScaledMouseY(client),
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
                VersionCompatImpl.getScaledMouseX(client) >= row.x &&
                    VersionCompatImpl.getScaledMouseX(client) < row.x + row.width &&
                    VersionCompatImpl.getScaledMouseY(client) >= row.y &&
                    VersionCompatImpl.getScaledMouseY(client) < row.y + row.height
            }
    }

    private var isDraggingSlider: Boolean = false

    fun isHoveringFlyingSpeedTrack(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return false
        }
        val bounds = AxionHudLayout.flyingSpeedSliderBounds(screenWidth, screenHeight, SavedHotbarController.selectedPage())
        return bounds.track.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun isHoveringFlyingSpeedPlusButton(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return false
        }
        val bounds = AxionHudLayout.flyingSpeedSliderBounds(screenWidth, screenHeight, SavedHotbarController.selectedPage())
        return bounds.plusButton.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun isHoveringFlyingSpeedMinusButton(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return false
        }
        val bounds = AxionHudLayout.flyingSpeedSliderBounds(screenWidth, screenHeight, SavedHotbarController.selectedPage())
        return bounds.minusButton.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun handleFlyingSpeedSliderDrag(client: MinecraftClient, screenWidth: Int, screenHeight: Int) {
        if (!isDraggingSlider) return
        val bounds = AxionHudLayout.flyingSpeedSliderBounds(screenWidth, screenHeight, SavedHotbarController.selectedPage())
        val mouseY = VersionCompatImpl.getScaledMouseY(client)
        val newValue = bounds.trackValueFromY(mouseY)
        AxionClientState.updateFlySpeedMultiplier(newValue)
    }

    fun handleFlyingSpeedSliderScroll(client: MinecraftClient, scrollDelta: Double): Boolean {
        if (!isHoveringFlyingSpeedTrack(client, client.window.scaledWidth, client.window.scaledHeight)) {
            return false
        }
        val currentValue = AxionClientState.flySpeedMultiplier
        val newValue = if (scrollDelta > 0) {
            (currentValue + 0.1f).coerceAtMost(9.99f)
        } else {
            (currentValue - 0.1f).coerceAtLeast(1.0f)
        }
        AxionClientState.updateFlySpeedMultiplier(newValue)
        return true
    }

    fun handleMouseButton(client: MinecraftClient, button: Int, action: Int): Boolean {
        if (SavedHotbarController.isOverlayActive(client)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                // Check for + button click
                if (isHoveringFlyingSpeedPlusButton(client, client.window.scaledWidth, client.window.scaledHeight)) {
                    val newValue = (AxionClientState.flySpeedMultiplier + 0.5f).coerceAtMost(9.99f)
                    AxionClientState.updateFlySpeedMultiplier(newValue)
                    return true
                }
                // Check for - button click
                if (isHoveringFlyingSpeedMinusButton(client, client.window.scaledWidth, client.window.scaledHeight)) {
                    val newValue = (AxionClientState.flySpeedMultiplier - 0.5f).coerceAtLeast(1.0f)
                    AxionClientState.updateFlySpeedMultiplier(newValue)
                    return true
                }
                // Check for slider drag start
                if (isHoveringFlyingSpeedTrack(client, client.window.scaledWidth, client.window.scaledHeight)) {
                    isDraggingSlider = true
                    // Immediately update value based on click position
                    handleFlyingSpeedSliderDrag(client, client.window.scaledWidth, client.window.scaledHeight)
                    return true
                }
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
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_RELEASE) {
                isDraggingSlider = false
            }
            // Handle slider drag during mouse move (via onEndTick or similar)
            if (isDraggingSlider) {
                handleFlyingSpeedSliderDrag(client, client.window.scaledWidth, client.window.scaledHeight)
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

package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.compat.CrowBarCompat
import axion.client.compat.LitematicaCompat
import axion.client.compat.VersionCompatImpl
import axion.client.config.AxionClientConfig
import axion.client.config.AxionConfigScreen
import axion.client.input.AxionModifierKeys
import axion.client.tool.AxionToolSelectionController
import axion.common.compat.VersionCompat
import axion.common.model.AxionSubtool
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.item.ItemStack
import net.minecraft.util.Arm
import java.util.Base64
import org.lwjgl.glfw.GLFW

object AxionAltMenuController {
    private var cursorUnlockedByAxion: Boolean = false

    var grabbedStack: ItemStack = ItemStack.EMPTY
        private set
    private var grabbedData: String? = null
    private var grabbedHotbarIndex: Int = -1
    private var grabbedSlotIndex: Int = -1
    private var grabbedFromLive: Boolean = false

    fun isActive(client: MinecraftClient): Boolean {
        return client.currentScreen == null &&
            AxionToolSelectionController.isAxionSelected() &&
            AxionModifierKeys.isAltDown(client) &&
            !LitematicaCompat.isHoldingConfiguredTool(client)
    }

    fun isAnyAltOverlayActive(client: MinecraftClient): Boolean {
        return isActive(client) || SavedHotbarController.isOverlayActive(client)
    }

    fun onEndTick(client: MinecraftClient) {
        if (client.currentScreen != null) {
            restoreGrabbedItem(client)
            CrowBarCompat.setLocatorBarSuppressed(false)
            return
        }

        // Handle continuous slider dragging
        if (isDraggingSlider) {
            handleFlyingSpeedSliderDrag(client, client.window.scaledWidth, client.window.scaledHeight)
        }

        val active = isAnyAltOverlayActive(client)
        CrowBarCompat.setLocatorBarSuppressed(active, keepVanillaLocatorBar = false)
        if (active) {
            if (client.mouse.isCursorLocked) {
                client.mouse.unlockCursor()
                cursorUnlockedByAxion = true
            }
            return
        }

        // Restore any grabbed item when overlay closes
        restoreGrabbedItem(client)

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

    fun isHoveringFinishTesting(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isAnyAltOverlayActive(client) || !AxionDevTestSession.isActive) {
            return false
        }

        val bounds = if (SavedHotbarController.isOverlayActive(client)) {
            AxionHudLayout.finishTestingSavedHotbarBounds(
                screenWidth,
                screenHeight,
                SavedHotbarController.selectedPage(),
            )
        } else {
            val sideSlot = AxionHudLayout.sideSlot(client, screenWidth, screenHeight)
            AxionHudLayout.finishTestingBounds(sideSlot)
        }
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

    fun hoveringSavedHotbarActionButton(
        client: MinecraftClient,
        screenWidth: Int,
        screenHeight: Int,
    ): AxionHudLayout.SavedHotbarActionButtonBounds? {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return null
        }

        val mouseX = VersionCompatImpl.getScaledMouseX(client)
        val mouseY = VersionCompatImpl.getScaledMouseY(client)
        return AxionHudLayout.savedHotbarActionButtons(
            screenWidth,
            screenHeight,
            SavedHotbarController.selectedPage(),
        ).firstOrNull { it.contains(mouseX, mouseY) }
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

    fun isHoveringToolboxButton(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return false
        }
        val bounds = AxionHudLayout.toolboxSlotBounds(client, screenWidth, screenHeight)
        val mouseX = VersionCompatImpl.getScaledMouseX(client)
        val mouseY = VersionCompatImpl.getScaledMouseY(client)
        return mouseX >= bounds.x &&
            mouseX < bounds.x + bounds.size &&
            mouseY >= bounds.y &&
            mouseY < bounds.y + bounds.size
    }

    fun isHoveringBinSlot(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Boolean {
        if (!SavedHotbarController.isOverlayActive(client)) return false
        val centerX = screenWidth / 2
        val mainX = when (client.options.mainArm.value) {
            Arm.LEFT -> centerX - 109
            Arm.RIGHT -> centerX + 109
        }
        val mouseX = VersionCompatImpl.getScaledMouseX(client)
        val mouseY = VersionCompatImpl.getScaledMouseY(client)
        return mouseX >= mainX - 11 && mouseX < mainX + 13 && mouseY >= screenHeight - 22 && mouseY < screenHeight
    }

    private fun findAnySlotAtPosition(client: MinecraftClient, screenWidth: Int, screenHeight: Int): Pair<Int, Int>? {
        val page = SavedHotbarController.selectedPage()
        val rows = AxionHudLayout.savedHotbarRows(screenWidth, screenHeight, page)
        val mouseX = VersionCompatImpl.getScaledMouseX(client).toInt()
        val mouseY = VersionCompatImpl.getScaledMouseY(client).toInt()
        for (row in rows) {
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

    private fun grabItemAtSlot(client: MinecraftClient, hotbarIndex: Int, slotIndex: Int): Boolean {
        val world = client.world ?: return false
        val player = client.player ?: return false
        val activeIndex = SavedHotbarController.activeIndex()
        if (hotbarIndex == activeIndex) {
            val stack = player.inventory.getStack(slotIndex)
            if (stack.isEmpty) return false
            val copy = stack.copy()
            player.inventory.setStack(slotIndex, ItemStack.EMPTY)
            client.interactionManager?.clickCreativeStack(ItemStack.EMPTY, 36 + slotIndex)
            grabbedStack = copy
            grabbedHotbarIndex = hotbarIndex
            grabbedSlotIndex = slotIndex
            grabbedFromLive = true
            grabbedData = null
            return true
        } else {
            val config = AxionClientConfig.savedHotbar(hotbarIndex) ?: return false
            val serialized = config.slots.getOrNull(slotIndex) ?: return false
            SavedHotbarController.setSlotItem(hotbarIndex, slotIndex, null)
            grabbedData = serialized
            grabbedHotbarIndex = hotbarIndex
            grabbedSlotIndex = slotIndex
            grabbedFromLive = false
            grabbedStack = SavedHotbarController.deserializeStackForDisplay(world.registryManager, serialized)
            return true
        }
    }

    private fun placeGrabbedItemAtSlot(client: MinecraftClient, hotbarIndex: Int, slotIndex: Int): Boolean {
        val world = client.world ?: return false
        val player = client.player ?: return false
        val heldStack = grabbedStack
        if (heldStack.isEmpty) return false
        val activeIndex = SavedHotbarController.activeIndex()
        if (hotbarIndex == activeIndex) {
            val existing = player.inventory.getStack(slotIndex)
            val existingStack = if (existing.isEmpty) null else existing.copy()
            player.inventory.setStack(slotIndex, heldStack.copy())
            client.interactionManager?.clickCreativeStack(heldStack.copy(), 36 + slotIndex)
            if (existingStack != null) {
                grabbedStack = existingStack
                grabbedHotbarIndex = hotbarIndex
                grabbedSlotIndex = slotIndex
                grabbedFromLive = true
                grabbedData = null
            } else {
                cancelGrab()
            }
        } else {
            val existingSerialized = AxionClientConfig.savedHotbar(hotbarIndex)?.slots?.getOrNull(slotIndex)
            val registryManager = world.registryManager
            val heldBytes = VersionCompat.INSTANCE.itemStackEncode(registryManager, heldStack)
            val heldSerialized = if (heldBytes != null) Base64.getEncoder().encodeToString(heldBytes) else null
            SavedHotbarController.setSlotItem(hotbarIndex, slotIndex, heldSerialized)
            if (existingSerialized != null) {
                grabbedData = existingSerialized
                grabbedHotbarIndex = hotbarIndex
                grabbedSlotIndex = slotIndex
                grabbedFromLive = false
                grabbedStack = SavedHotbarController.deserializeStackForDisplay(registryManager, existingSerialized)
            } else {
                cancelGrab()
            }
        }
        return true
    }

    private fun restoreGrabbedItem(client: MinecraftClient) {
        if (grabbedStack.isEmpty) return
        val player = client.player ?: return cancelGrab()
        if (grabbedFromLive) {
            player.inventory.setStack(grabbedSlotIndex, grabbedStack.copy())
            client.interactionManager?.clickCreativeStack(grabbedStack.copy(), 36 + grabbedSlotIndex)
        } else if (grabbedData != null) {
            SavedHotbarController.setSlotItem(grabbedHotbarIndex, grabbedSlotIndex, grabbedData)
        } else {
            val world = client.world
            if (world != null) {
                val bytes = VersionCompat.INSTANCE.itemStackEncode(world.registryManager, grabbedStack)
                val serialized = if (bytes != null) Base64.getEncoder().encodeToString(bytes) else null
                SavedHotbarController.setSlotItem(grabbedHotbarIndex, grabbedSlotIndex, serialized)
            }
        }
        cancelGrab()
    }

    private fun cancelGrab() {
        grabbedStack = ItemStack.EMPTY
        grabbedData = null
        grabbedHotbarIndex = -1
        grabbedSlotIndex = -1
        grabbedFromLive = false
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

    private val CAPABILITY_NAMES = mapOf(
        0 to "Bulldozer", 1 to "Replace Mode", 2 to "Force Place", 3 to "No Updates",
        5 to "Infinite Reach", 6 to "Fast Place", 7 to "Angel Placement",
        8 to "No Clip", 9 to "Phantom",
    )

    private fun capabilityEnabled(index: Int): Boolean {
        val state = AxionClientState.globalModeState
        return when (index) {
            0 -> state.bulldozerEnabled
            1 -> state.replaceModeEnabled
            2 -> state.forcePlaceEnabled
            3 -> state.noUpdatesEnabled
            5 -> state.infiniteReachEnabled
            6 -> state.fastPlaceEnabled
            7 -> state.angelPlacementEnabled
            8 -> state.noClipEnabled
            9 -> state.phantomEnabled
            else -> false
        }
    }

    private fun handleCapabilityClick(client: MinecraftClient): Boolean {
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        val centerX = screenWidth / 2
        val offX = when (client.options.mainArm.value) {
            Arm.LEFT -> centerX + 107
            Arm.RIGHT -> centerX - 107
        }
        val mouseX = VersionCompatImpl.getScaledMouseX(client)
        val mouseY = VersionCompatImpl.getScaledMouseY(client)
        for (index in 0 until 10) {
            val y = screenHeight - 44 - 22 * index
            if (mouseX >= offX - 10 && mouseX < offX + 10 && mouseY >= y && mouseY < y + 20) {
                val state = AxionClientState.globalModeState
                val nextState = when (index) {
                    0 -> state.copy(bulldozerEnabled = !state.bulldozerEnabled)
                    1 -> state.copy(replaceModeEnabled = !state.replaceModeEnabled)
                    2 -> state.copy(forcePlaceEnabled = !state.forcePlaceEnabled)
                    3 -> state.copy(noUpdatesEnabled = !state.noUpdatesEnabled)
                    5 -> state.copy(infiniteReachEnabled = !state.infiniteReachEnabled)
                    6 -> state.copy(fastPlaceEnabled = !state.fastPlaceEnabled)
                    7 -> state.copy(angelPlacementEnabled = !state.angelPlacementEnabled)
                    8 -> state.copy(noClipEnabled = !state.noClipEnabled)
                    9 -> state.copy(phantomEnabled = !state.phantomEnabled)
                    else -> return true
                }
                if (nextState != state) {
                    AxionClientState.updateGlobalModes(nextState)
                    val name = CAPABILITY_NAMES[index] ?: return true
                    val enabled = capabilityEnabled(index)
                    val player = client.player
                    if (player != null) {
                        VersionCompat.INSTANCE.playerSendMessage(
                            player,
                            Text.literal("${if (enabled) "Enabled" else "Disabled"}: $name"),
                            true,
                        )
                    }
                }
                return true
            }
        }
        return false
    }

    fun handleMouseButton(client: MinecraftClient, button: Int, action: Int): Boolean {
        if (SavedHotbarController.isOverlayActive(client)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                if (isHoveringFinishTesting(client, client.window.scaledWidth, client.window.scaledHeight)) {
                    VanillaHudButtonStore.click(client, VanillaHudButtonStore.FINISH_TESTING, button)
                    AxionDevTestSession.finish(client)
                    return true
                }
                hoveringSavedHotbarActionButton(
                    client,
                    client.window.scaledWidth,
                    client.window.scaledHeight,
                )?.let { actionButton ->
                    VanillaHudButtonStore.click(
                        client,
                        VanillaHudButtonStore.actionKey(actionButton.action),
                        button,
                    )
                    if (actionButton.enabled && actionButton.action.gameModeId != null) {
                        restoreGrabbedItem(client)
                        SavedHotbarController.flushActiveHotbar(client)
                        SavedHotbarGameModeController.request(client, actionButton.action)
                    }
                    return true
                }

                val slot = findAnySlotAtPosition(client, client.window.scaledWidth, client.window.scaledHeight)

                if (slot != null) {
                    if (!grabbedStack.isEmpty) {
                        placeGrabbedItemAtSlot(client, slot.first, slot.second)
                        return true
                    }
                    if (grabItemAtSlot(client, slot.first, slot.second)) {
                        return true
                    }
                }

                if (!grabbedStack.isEmpty && isHoveringBinSlot(client, client.window.scaledWidth, client.window.scaledHeight)) {
                    cancelGrab()
                    return true
                }

                if (grabbedStack.isEmpty && isHoveringBinSlot(client, client.window.scaledWidth, client.window.scaledHeight) && AxionModifierKeys.isShiftDown(client)) {
                    SavedHotbarController.clearPage(SavedHotbarController.selectedPage())
                    return true
                }

                if (isHoveringFlyingSpeedPlusButton(client, client.window.scaledWidth, client.window.scaledHeight)) {
                    val newValue = (AxionClientState.flySpeedMultiplier + 0.5f).coerceAtMost(9.99f)
                    AxionClientState.updateFlySpeedMultiplier(newValue)
                    return true
                }

                if (isHoveringFlyingSpeedMinusButton(client, client.window.scaledWidth, client.window.scaledHeight)) {
                    val newValue = (AxionClientState.flySpeedMultiplier - 0.5f).coerceAtLeast(1.0f)
                    AxionClientState.updateFlySpeedMultiplier(newValue)
                    return true
                }

                if (isHoveringFlyingSpeedTrack(client, client.window.scaledWidth, client.window.scaledHeight)) {
                    isDraggingSlider = true
                    handleFlyingSpeedSliderDrag(client, client.window.scaledWidth, client.window.scaledHeight)
                    return true
                }

                if (handleCapabilityClick(client)) {
                    return true
                }

                if (isHoveringToolboxButton(client, client.window.scaledWidth, client.window.scaledHeight)) {
                    cursorUnlockedByAxion = false
                    isDraggingSlider = false
                    client.setScreen(AxionConfigScreen(null))
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

            if (isDraggingSlider) {
                handleFlyingSpeedSliderDrag(client, client.window.scaledWidth, client.window.scaledHeight)
            }
            return true
        }

        if (!isActive(client)) {
            return false
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            if (isHoveringFinishTesting(client, client.window.scaledWidth, client.window.scaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.FINISH_TESTING, button)
                AxionDevTestSession.finish(client)
                return true
            }
            if (isHoveringMiddleClickToggle(client, client.window.scaledWidth, client.window.scaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.MIDDLE_CLICK, button)
                AxionClientState.updateMiddleClickMagicSelect(!AxionClientState.middleClickMagicSelectEnabled)
                return true
            }
            if (isHoveringKeepExistingToggle(client, client.window.scaledWidth, client.window.scaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.KEEP_EXISTING, button)
                AxionClientState.updateKeepExisting(!AxionClientState.keepExistingEnabled)
                return true
            }
            if (isHoveringCopyEntitiesToggle(client, client.window.scaledWidth, client.window.scaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.COPY_ENTITIES, button)
                AxionClientState.updateCopyEntities(!AxionClientState.copyEntitiesEnabled)
                return true
            }
            if (isHoveringCopyAirToggle(client, client.window.scaledWidth, client.window.scaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.COPY_AIR, button)
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

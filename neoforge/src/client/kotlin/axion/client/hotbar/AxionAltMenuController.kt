package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.compat.CrowBarCompat
import axion.client.compat.LitematicaCompat
import axion.client.compat.VersionCompatImpl
import axion.client.config.AxionClientConfig
import axion.client.config.AxionConfigScreen
import axion.client.input.AxionModifierKeys
import axion.client.itemStack.AxionToolSelectionController
import axion.common.compat.VersionCompat
import axion.common.model.AxionSubtool
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.HumanoidArm
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

    fun isActive(client: Minecraft): Boolean {
        return client.screen == null &&
            AxionToolSelectionController.isAxionSelected() &&
            AxionModifierKeys.isAltDown(client) &&
            !LitematicaCompat.isHoldingConfiguredTool(client)
    }

    fun isAnyAltOverlayActive(client: Minecraft): Boolean {
        return isActive(client) || SavedHotbarController.isOverlayActive(client)
    }

    fun onEndTick(client: Minecraft) {
        if (client.screen != null) {
            restoreGrabbedItem(client)
            CrowBarCompat.setLocatorBarSuppressed(false)
            return
        }

        // Handle continuous slider dragging
        if (isDraggingSlider) {
            handleFlyingSpeedSliderDrag(client, client.window.guiScaledWidth, client.window.guiScaledHeight)
        }

        val active = isAnyAltOverlayActive(client)
        CrowBarCompat.setLocatorBarSuppressed(active, keepVanillaLocatorBar = false)
        if (active) {
            if (client.mouseHandler.isMouseGrabbed) {
                client.mouseHandler.releaseMouse()
                cursorUnlockedByAxion = true
            }
            return
        }

        // Restore any grabbed item when overlay closes
        restoreGrabbedItem(client)

        if (cursorUnlockedByAxion && !client.mouseHandler.isMouseGrabbed) {
            client.mouseHandler.grabMouse()
        }
        cursorUnlockedByAxion = false
    }

    fun hoveredSubtool(client: Minecraft, screenWidth: Int, screenHeight: Int): AxionSubtool? {
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

    fun isHoveringMiddleClickToggle(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
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

    fun isHoveringFinishTesting(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
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

    fun isHoveringKeepExistingToggle(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
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

    fun isHoveringCopyEntitiesToggle(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
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

    fun isHoveringCopyAirToggle(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
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
        client: Minecraft,
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
        client: Minecraft,
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
        client: Minecraft,
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

    fun isHoveringFlyingSpeedTrack(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return false
        }
        val bounds = AxionHudLayout.flyingSpeedSliderBounds(screenWidth, screenHeight, SavedHotbarController.selectedPage())
        return bounds.connect.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun isHoveringFlyingSpeedPlusButton(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return false
        }
        val bounds = AxionHudLayout.flyingSpeedSliderBounds(screenWidth, screenHeight, SavedHotbarController.selectedPage())
        return bounds.plusButton.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun isHoveringFlyingSpeedMinusButton(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
        if (!SavedHotbarController.isOverlayActive(client)) {
            return false
        }
        val bounds = AxionHudLayout.flyingSpeedSliderBounds(screenWidth, screenHeight, SavedHotbarController.selectedPage())
        return bounds.minusButton.contains(
            VersionCompatImpl.getScaledMouseX(client),
            VersionCompatImpl.getScaledMouseY(client),
        )
    }

    fun isHoveringToolboxButton(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
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

    fun isHoveringBinSlot(client: Minecraft, screenWidth: Int, screenHeight: Int): Boolean {
        if (!SavedHotbarController.isOverlayActive(client)) return false
        val centerX = screenWidth / 2
        val mainX = when (client.options.mainArm.value) {
            HumanoidArm.LEFT -> centerX - 109
            HumanoidArm.RIGHT -> centerX + 109
        }
        val mouseX = VersionCompatImpl.getScaledMouseX(client)
        val mouseY = VersionCompatImpl.getScaledMouseY(client)
        return mouseX >= mainX - 11 && mouseX < mainX + 13 && mouseY >= screenHeight - 22 && mouseY < screenHeight
    }

    private fun findAnySlotAtPosition(client: Minecraft, screenWidth: Int, screenHeight: Int): Pair<Int, Int>? {
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

    private fun grabItemAtSlot(client: Minecraft, hotbarIndex: Int, slotIndex: Int): Boolean {
        val world = client.level ?: return false
        val player = client.player ?: return false
        val activeIndex = SavedHotbarController.activeIndex()
        if (hotbarIndex == activeIndex) {
            val stack = player.inventory.getStack(slotIndex)
            if (stack.isEmpty) return false
            val copy = stack.copy()
            player.inventory.setStack(slotIndex, ItemStack.EMPTY)
            client.gameMode?.clickCreativeStack(ItemStack.EMPTY, 36 + slotIndex)
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
            grabbedStack = SavedHotbarController.deserializeStackForDisplay(world.registryAccess, serialized)
            return true
        }
    }

    private fun placeGrabbedItemAtSlot(client: Minecraft, hotbarIndex: Int, slotIndex: Int): Boolean {
        val world = client.level ?: return false
        val player = client.player ?: return false
        val heldStack = grabbedStack
        if (heldStack.isEmpty) return false
        val activeIndex = SavedHotbarController.activeIndex()
        if (hotbarIndex == activeIndex) {
            val existing = player.inventory.getStack(slotIndex)
            val existingStack = if (existing.isEmpty) null else existing.copy()
            player.inventory.setStack(slotIndex, heldStack.copy())
            client.gameMode?.clickCreativeStack(heldStack.copy(), 36 + slotIndex)
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
            val registryManager = world.registryAccess
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

    private fun restoreGrabbedItem(client: Minecraft) {
        if (grabbedStack.isEmpty) return
        val player = client.player ?: return cancelGrab()
        if (grabbedFromLive) {
            player.inventory.setStack(grabbedSlotIndex, grabbedStack.copy())
            client.gameMode?.clickCreativeStack(grabbedStack.copy(), 36 + grabbedSlotIndex)
        } else if (grabbedData != null) {
            SavedHotbarController.setSlotItem(grabbedHotbarIndex, grabbedSlotIndex, grabbedData)
        } else {
            val world = client.level
            if (world != null) {
                val bytes = VersionCompat.INSTANCE.itemStackEncode(world.registryAccess, grabbedStack)
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

    fun handleFlyingSpeedSliderDrag(client: Minecraft, screenWidth: Int, screenHeight: Int) {
        if (!isDraggingSlider) return
        val bounds = AxionHudLayout.flyingSpeedSliderBounds(screenWidth, screenHeight, SavedHotbarController.selectedPage())
        val mouseY = VersionCompatImpl.getScaledMouseY(client)
        val newValue = bounds.trackValueFromY(mouseY)
        AxionClientState.updateFlySpeedMultiplier(newValue)
    }

    fun handleFlyingSpeedSliderScroll(client: Minecraft, scrollDelta: Double): Boolean {
        if (!isHoveringFlyingSpeedTrack(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
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

    private fun handleCapabilityClick(client: Minecraft): Boolean {
        val screenWidth = client.window.guiScaledWidth
        val screenHeight = client.window.guiScaledHeight
        val centerX = screenWidth / 2
        val offX = when (client.options.mainArm.value) {
            HumanoidArm.LEFT -> centerX + 107
            HumanoidArm.RIGHT -> centerX - 107
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
                            Component.literal("${if (enabled) "Enabled" else "Disabled"}: $name"),
                            true,
                        )
                    }
                }
                return true
            }
        }
        return false
    }

    fun handleMouseButton(client: Minecraft, button: Int, action: Int): Boolean {
        if (SavedHotbarController.isOverlayActive(client)) {
            if (button == GLFW.MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                if (isHoveringFinishTesting(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                    VanillaHudButtonStore.click(client, VanillaHudButtonStore.FINISH_TESTING, button)
                    AxionDevTestSession.finish(client)
                    return true
                }
                hoveringSavedHotbarActionButton(
                    client,
                    client.window.guiScaledWidth,
                    client.window.guiScaledHeight,
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

                val slot = findAnySlotAtPosition(client, client.window.guiScaledWidth, client.window.guiScaledHeight)

                if (slot != null) {
                    if (!grabbedStack.isEmpty) {
                        placeGrabbedItemAtSlot(client, slot.first, slot.second)
                        return true
                    }
                    if (grabItemAtSlot(client, slot.first, slot.second)) {
                        return true
                    }
                }

                if (!grabbedStack.isEmpty && isHoveringBinSlot(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                    cancelGrab()
                    return true
                }

                if (grabbedStack.isEmpty && isHoveringBinSlot(client, client.window.guiScaledWidth, client.window.guiScaledHeight) && AxionModifierKeys.isShiftDown(client)) {
                    SavedHotbarController.clearPage(SavedHotbarController.selectedPage())
                    return true
                }

                if (isHoveringFlyingSpeedPlusButton(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                    val newValue = (AxionClientState.flySpeedMultiplier + 0.5f).coerceAtMost(9.99f)
                    AxionClientState.updateFlySpeedMultiplier(newValue)
                    return true
                }

                if (isHoveringFlyingSpeedMinusButton(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                    val newValue = (AxionClientState.flySpeedMultiplier - 0.5f).coerceAtLeast(1.0f)
                    AxionClientState.updateFlySpeedMultiplier(newValue)
                    return true
                }

                if (isHoveringFlyingSpeedTrack(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                    isDraggingSlider = true
                    handleFlyingSpeedSliderDrag(client, client.window.guiScaledWidth, client.window.guiScaledHeight)
                    return true
                }

                if (handleCapabilityClick(client)) {
                    return true
                }

                if (isHoveringToolboxButton(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                    cursorUnlockedByAxion = false
                    isDraggingSlider = false
                    client.setScreen(AxionConfigScreen(null))
                    return true
                }

                hoveringSavedHotbarPageButton(
                    client,
                    client.window.guiScaledWidth,
                    client.window.guiScaledHeight,
                )?.let { buttonBounds ->
                    SavedHotbarController.changePage(buttonBounds.direction)
                    return true
                }

                hoveringSavedHotbarRow(
                    client,
                    client.window.guiScaledWidth,
                    client.window.guiScaledHeight,
                )?.let { rowBounds ->
                    SavedHotbarController.selectHotbar(rowBounds.index)
                }
            }

            if (button == GLFW.MOUSE_BUTTON_LEFT && action == GLFW.GLFW_RELEASE) {
                isDraggingSlider = false
            }

            if (isDraggingSlider) {
                handleFlyingSpeedSliderDrag(client, client.window.guiScaledWidth, client.window.guiScaledHeight)
            }
            return true
        }

        if (!isActive(client)) {
            return false
        }

        if (button == GLFW.MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            if (isHoveringFinishTesting(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.FINISH_TESTING, button)
                AxionDevTestSession.finish(client)
                return true
            }
            if (isHoveringMiddleClickToggle(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.MIDDLE_CLICK, button)
                AxionClientState.updateMiddleClickMagicSelect(!AxionClientState.middleClickMagicSelectEnabled)
                return true
            }
            if (isHoveringKeepExistingToggle(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.KEEP_EXISTING, button)
                AxionClientState.updateKeepExisting(!AxionClientState.keepExistingEnabled)
                return true
            }
            if (isHoveringCopyEntitiesToggle(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.COPY_ENTITIES, button)
                AxionClientState.updateCopyEntities(!AxionClientState.copyEntitiesEnabled)
                return true
            }
            if (isHoveringCopyAirToggle(client, client.window.guiScaledWidth, client.window.guiScaledHeight)) {
                VanillaHudButtonStore.click(client, VanillaHudButtonStore.COPY_AIR, button)
                AxionClientState.updateCopyAir(!AxionClientState.copyAirEnabled)
                return true
            }
            hoveredSubtool(
                client = client,
                screenWidth = client.window.guiScaledWidth,
                screenHeight = client.window.guiScaledHeight,
            )?.let(AxionToolSelectionController::selectSubtool)
        }
        return true
    }
}

package axion.client.mode
import axion.client.AxionClientState
import axion.client.input.AxionKeybindings
import axion.client.network.AxionServerConnection
import axion.client.symmetry.ActiveSymmetryConfig
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.client.symmetry.SymmetryBreakController
import axion.client.tool.AxionToolSelectionController
import axion.common.model.BlockRegion
import axion.common.operation.ClearRegionOperation
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.client.toast.SystemToast
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.world.WorldEvents

object ClientModeController {
    private val dispatcher = SymmetryAwareOperationDispatcher(recordHistory = false)
    private var suppressPrimaryUntilRelease: Boolean = false
    private var suppressSecondaryUntilRelease: Boolean = false

    fun enforceCreativeMode(client: MinecraftClient) {
        if (canUseModes(client)) {
            return
        }

        val player = client.player
        if (player != null) {
            player.noClip = player.isSpectator
        }
        client.server
            ?.playerManager
            ?.getPlayer(player?.uuid)
            ?.let { serverPlayer ->
                serverPlayer.noClip = serverPlayer.isSpectator
            }

        suppressPrimaryUntilRelease = false
        suppressSecondaryUntilRelease = false
        if (AxionClientState.globalModeState != axion.common.model.GlobalModeState()) {
            AxionClientState.updateGlobalModes(axion.common.model.GlobalModeState())
        }
    }

    fun onEndTick(client: MinecraftClient) {
        if (!canUseModes(client)) {
            applyNoClip(client)
            syncRemoteNoClip(client)
            return
        }

        if (client.currentScreen == null) {
            if (client.options.useKey.isPressed && !suppressSecondaryUntilRelease) {
                consumeSecondaryAction(client)
            }
        }

        if (!client.options.attackKey.isPressed) {
            suppressPrimaryUntilRelease = false
        } else if (suppressPrimaryUntilRelease) {
            client.interactionManager?.cancelBlockBreaking()
        }

        if (!client.options.useKey.isPressed) {
            suppressSecondaryUntilRelease = false
        }

        applyNoClip(client)
        syncRemoteNoClip(client)
    }

    fun handleToggleKeypresses(client: MinecraftClient) {
        if (!canUseModes(client)) {
            return
        }

        while (AxionKeybindings.toggleNoClip.wasPressed()) {
            toggleNoClip(client)
        }

        while (AxionKeybindings.toggleReplaceMode.wasPressed()) {
            toggleReplaceMode(client)
        }

        while (AxionKeybindings.toggleInfiniteReach.wasPressed()) {
            toggleInfiniteReach(client)
        }
    }

    fun shouldSuppressPrimary(client: MinecraftClient): Boolean {
        if (!suppressPrimaryUntilRelease) {
            return false
        }
        if (!client.options.attackKey.isPressed) {
            suppressPrimaryUntilRelease = false
            return false
        }
        client.interactionManager?.cancelBlockBreaking()
        return true
    }

    fun shouldSuppressSecondary(client: MinecraftClient): Boolean {
        if (!suppressSecondaryUntilRelease) {
            return false
        }
        if (!client.options.useKey.isPressed) {
            suppressSecondaryUntilRelease = false
            return false
        }
        return true
    }

    fun ownsPrimaryAction(client: MinecraftClient): Boolean {
        if (!canUseModes(client)) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        if (!AxionClientState.globalModeState.infiniteReachEnabled) {
            return false
        }
        return ModeTargeting.currentBlockTarget(client)?.beyondVanillaReach == true
    }

    fun consumePrimaryAction(client: MinecraftClient): Boolean {
        if (!canUseModes(client)) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        if (!AxionClientState.globalModeState.infiniteReachEnabled) {
            return false
        }

        val target = ModeTargeting.currentBlockTarget(client) ?: return false
        if (!target.beyondVanillaReach) {
            return false
        }
        val world = client.world ?: return false
        val targetPos = target.hitResult.blockPos.toImmutable()
        val brokenState = world.getBlockState(targetPos)
        suppressPrimaryUntilRelease = true
        client.interactionManager?.cancelBlockBreaking()

        dispatcher.dispatch(
            ClearRegionOperation(
                BlockRegion(targetPos, targetPos),
            ),
        )
        SymmetryBreakController.dispatchDerivedBreaks(client, targetPos)
        client.player?.swingHand(Hand.MAIN_HAND)
        if (!brokenState.isAir) {
            playBreakEffects(client, targetPos, brokenState)
        }
        return true
    }

    fun consumeHeldPrimaryAction(client: MinecraftClient): Boolean {
        if (suppressPrimaryUntilRelease) {
            client.interactionManager?.cancelBlockBreaking()
            return true
        }
        return consumePrimaryAction(client)
    }

    fun consumeSecondaryAction(client: MinecraftClient): Boolean {
        if (!canUseModes(client)) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }

        val state = AxionClientState.globalModeState
        if (!state.replaceModeEnabled && !state.infiniteReachEnabled) {
            return false
        }

        val target = ModeTargeting.currentBlockTarget(client) ?: return false
        if (!state.infiniteReachEnabled && target.beyondVanillaReach) {
            return false
        }
        if (!state.replaceModeEnabled && !target.beyondVanillaReach) {
            return false
        }

        val operation = BuildPlacementService.createPlacementOperation(
            client = client,
            target = target,
            symmetryConfig = ActiveSymmetryConfig.current()
                ?.takeIf(ActiveSymmetryConfig::hasDerivedTransforms),
            replaceMode = state.replaceModeEnabled,
        ) ?: return false
        dispatcher.dispatch(operation)
        client.player?.swingHand(Hand.MAIN_HAND)
        playPlacementEffects(client, operation)
        suppressSecondaryUntilRelease = true
        client.player?.stopUsingItem()
        return true
    }

    fun consumeMiddleAction(client: MinecraftClient): Boolean {
        if (!canUseModes(client)) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        if (!AxionClientState.globalModeState.infiniteReachEnabled) {
            return false
        }

        val target = ModeTargeting.currentBlockTarget(client) ?: return false
        if (!target.beyondVanillaReach) {
            return false
        }

        return tryPickFarBlock(client, target.hitResult.blockPos)
    }

    private fun toggleNoClip(client: MinecraftClient) {
        val nextState = AxionClientState.globalModeState.copy(
            noClipEnabled = !AxionClientState.globalModeState.noClipEnabled,
        )
        AxionClientState.updateGlobalModes(nextState)
        if (nextState.noClipEnabled) {
            enableFlightForNoClip(client)
        }
        showToast(client, "No Clip", nextState.noClipEnabled)
    }

    private fun toggleReplaceMode(client: MinecraftClient) {
        val nextState = AxionClientState.globalModeState.copy(
            replaceModeEnabled = !AxionClientState.globalModeState.replaceModeEnabled,
        )
        AxionClientState.updateGlobalModes(nextState)
        showToast(client, "Replace Mode", nextState.replaceModeEnabled)
    }

    private fun toggleInfiniteReach(client: MinecraftClient) {
        val nextState = AxionClientState.globalModeState.copy(
            infiniteReachEnabled = !AxionClientState.globalModeState.infiniteReachEnabled,
        )
        AxionClientState.updateGlobalModes(nextState)
        showToast(client, "Infinite Reach", nextState.infiniteReachEnabled)
    }

    private fun applyNoClip(client: MinecraftClient) {
        val player = client.player ?: return
        val active = AxionClientState.globalModeState.noClipEnabled && player.abilities.flying
        player.noClip = player.isSpectator || active
        player.setNoGravity(player.isSpectator || active)
        client.server
            ?.playerManager
            ?.getPlayer(player.uuid)
            ?.let { serverPlayer ->
                serverPlayer.noClip = serverPlayer.isSpectator || active
                serverPlayer.setNoGravity(serverPlayer.isSpectator || active)
            }
    }

    private fun enableFlightForNoClip(client: MinecraftClient) {
        val player = client.player ?: return
        if (!player.abilities.allowFlying || player.abilities.flying) {
            return
        }

        player.abilities.flying = true
        player.sendAbilitiesUpdate()
        player.noClip = true
        player.setNoGravity(true)
        player.setOnGround(false)
        player.horizontalCollision = false
        player.verticalCollision = false
        client.server
            ?.playerManager
            ?.getPlayer(player.uuid)
            ?.let { serverPlayer ->
                if (serverPlayer.abilities.allowFlying && !serverPlayer.abilities.flying) {
                    serverPlayer.abilities.flying = true
                    serverPlayer.noClip = true
                    serverPlayer.setNoGravity(true)
                    serverPlayer.setOnGround(false)
                    serverPlayer.horizontalCollision = false
                    serverPlayer.verticalCollision = false
                    serverPlayer.sendAbilitiesUpdate()
                }
            }
    }

    private fun syncRemoteNoClip(client: MinecraftClient) {
        if (client.server != null) {
            return
        }

        val armed = canUseModes(client) && AxionClientState.globalModeState.noClipEnabled
        AxionServerConnection.syncNoClipState(armed)
    }

    fun isNoClipActiveFor(entity: Entity): Boolean {
        val playerEntity = entity as? PlayerEntity ?: return false
        val clientPlayer = MinecraftClient.getInstance().player ?: return false
        if (!AxionClientState.globalModeState.noClipEnabled || playerEntity.uuid != clientPlayer.uuid) {
            return false
        }
        return clientPlayer.isSpectator || clientPlayer.abilities.flying
    }

    private fun showToast(client: MinecraftClient, modeName: String, enabled: Boolean) {
        val message = Text.literal("Axion $modeName ${if (enabled) "enabled" else "disabled"}")
        client.inGameHud.setOverlayMessage(message, false)
        SystemToast.add(
            client.toastManager,
            SystemToast.Type.PERIODIC_NOTIFICATION,
            Text.literal("Axion $modeName"),
            Text.literal(if (enabled) "Enabled" else "Disabled"),
        )
    }

    private fun playBreakEffects(
        client: MinecraftClient,
        pos: net.minecraft.util.math.BlockPos,
        state: net.minecraft.block.BlockState,
    ) {
        val world = client.world ?: return
        val player = client.player
        world.syncWorldEvent(player, WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state))
    }

    private fun playPlacementEffects(
        client: MinecraftClient,
        operation: axion.common.operation.SymmetryPlacementOperation,
    ) {
        val world = client.world ?: return
        operation.placements.forEach { placement ->
            val soundGroup = placement.state.soundGroup
            world.playSoundClient(
                placement.pos.x + 0.5,
                placement.pos.y + 0.5,
                placement.pos.z + 0.5,
                soundGroup.placeSound,
                SoundCategory.BLOCKS,
                (soundGroup.volume + 1.0f) / 2.0f,
                soundGroup.pitch * 0.8f,
                false,
            )
        }
    }

    private fun tryPickFarBlock(
        client: MinecraftClient,
        pos: net.minecraft.util.math.BlockPos,
    ): Boolean {
        val world = client.world ?: return false
        val player = client.player ?: return false
        val state = world.getBlockState(pos)
        if (state.isAir) {
            return false
        }

        val pickedItem = state.block.asItem()
        if (pickedItem == Items.AIR) {
            return false
        }

        val inventory = player.inventory
        if (player.isInCreativeMode) {
            val hotbarSlot = findInventorySlot(inventory, pickedItem, 0 until PlayerInventory.getHotbarSize())
            if (hotbarSlot >= 0) {
                inventory.setSelectedSlot(hotbarSlot)
                return true
            }

            val inventorySlot = findInventorySlot(inventory, pickedItem, PlayerInventory.getHotbarSize() until inventory.mainStacks.size)
            if (inventorySlot >= 0) {
                client.interactionManager?.clickSlot(
                    player.currentScreenHandler.syncId,
                    inventorySlotToScreenSlot(inventorySlot),
                    inventory.selectedSlot,
                    SlotActionType.SWAP,
                    player,
                ) ?: return false
                inventory.setSelectedSlot(inventory.selectedSlot)
                return true
            }

            return clonePickedItemIntoHand(client, player, inventory, pickedItem.getDefaultStack())
        }

        val hotbarSlot = findInventorySlot(inventory, pickedItem, 0 until PlayerInventory.getHotbarSize())
        if (hotbarSlot >= 0) {
            inventory.setSelectedSlot(hotbarSlot)
            return true
        }

        val inventorySlot = findInventorySlot(inventory, pickedItem, PlayerInventory.getHotbarSize() until inventory.mainStacks.size)
        if (inventorySlot < 0) {
            return clonePickedItemIntoHand(client, player, inventory, pickedItem.getDefaultStack())
        }

        client.interactionManager?.clickSlot(
            player.currentScreenHandler.syncId,
            inventorySlotToScreenSlot(inventorySlot),
            inventory.selectedSlot,
            SlotActionType.SWAP,
            player,
        ) ?: return false
        inventory.setSelectedSlot(inventory.selectedSlot)
        return true
    }

    private fun clonePickedItemIntoHand(
        client: MinecraftClient,
        player: net.minecraft.client.network.ClientPlayerEntity,
        inventory: PlayerInventory,
        pickedStack: net.minecraft.item.ItemStack,
    ): Boolean {
        val interactionManager = client.interactionManager ?: return false
        if (!player.isInCreativeMode) {
            return false
        }

        val selectedSlot = inventory.selectedSlot
        val heldStack = inventory.getStack(selectedSlot)
        val emptySlot = inventory.getEmptySlot().takeIf { it >= 0 && it != selectedSlot }
        if (!heldStack.isEmpty && emptySlot != null) {
            interactionManager.clickSlot(
                player.currentScreenHandler.syncId,
                inventorySlotToScreenSlot(emptySlot),
                selectedSlot,
                SlotActionType.SWAP,
                player,
            )
        }

        inventory.setStack(selectedSlot, pickedStack.copy())
        interactionManager.clickCreativeStack(pickedStack, 36 + selectedSlot)
        return true
    }

    private fun canUseModes(client: MinecraftClient): Boolean {
        return client.player?.isInCreativeMode == true
    }

    private fun findInventorySlot(
        inventory: PlayerInventory,
        item: net.minecraft.item.Item,
        slots: IntRange,
    ): Int {
        return slots.firstOrNull { slot ->
            inventory.getStack(slot).item == item
        } ?: -1
    }

    private fun inventorySlotToScreenSlot(inventorySlot: Int): Int {
        return if (inventorySlot < PlayerInventory.getHotbarSize()) {
            36 + inventorySlot
        } else {
            inventorySlot
        }
    }
}

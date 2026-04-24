package axion.client.mode
import axion.client.AxionClientState
import axion.client.compat.VersionCompatImpl
import axion.client.input.AxionKeybindings
import axion.client.network.AxionServerConnection
import axion.client.selection.AxionTargeting
import axion.client.symmetry.ActiveSymmetryConfig
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.client.symmetry.SymmetryBreakController
import axion.client.tool.AxionToolSelectionController
import axion.common.model.BlockRegion
import axion.common.operation.ClearRegionOperation
import axion.AxionMod
import axion.mixin.client.ClientPlayerInteractionManagerAccessor
import axion.mixin.client.MinecraftClientAccessor
import net.minecraft.block.BlockState
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.block.Block
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.InputUtil
import net.minecraft.client.toast.SystemToast
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.world.RaycastContext
import net.minecraft.world.WorldEvents

object ClientModeController {
    private const val NO_CLIP_ESCAPE_TICKS: Int = 8
    private const val MULTI_SAMPLE_COUNT: Int = 50
    private val dispatcher = SymmetryAwareOperationDispatcher(recordHistory = false)
    private var suppressPrimaryUntilRelease: Boolean = false
    private var suppressSecondaryUntilRelease: Boolean = false
    private var noClipEscapeTicks: Int = 0
    private var previousAttackPressed: Boolean = false
    private var seenPlacementTargets = linkedSetOf<PlacementSampleTarget>()
    private var lastPlacementHitPos: Vec3d? = null
    private var lastPlacedBlockPos: net.minecraft.util.math.BlockPos? = null
    private var seenTargetsResetTimer: Int = 0
    private var lastPlacementTick: Long = 0
    private var fastPlaceExecutedThisTick: Boolean = false
    // Vanilla places blocks every 4 ticks (5 blocks/second)
    private const val VANILLA_PLACEMENT_COOLDOWN_TICKS: Int = 4

    // Sound cooldown to prevent spam during fast place mode
    private var lastPlacementSoundTick: Long = 0
    private const val PLACEMENT_SOUND_COOLDOWN_TICKS: Int = 4

    private var lastBreakTick: Long = 0
    // Vanilla breaking speed (4 tick cooldown)
    private const val VANILLA_BREAK_COOLDOWN_TICKS: Int = 4

    // Manual key tracking for when mixin cancels vanilla key handling
    private var useKeyManuallyPressed: Boolean = false
    private var attackKeyManuallyPressed: Boolean = false

    // Unified bulldozer cooldown - same speed for IR and non-IR
    private var lastBulldozerTick: Long = 0
    private const val BULLDOZER_COOLDOWN_TICKS: Int = 2  // Slightly faster than vanilla (4 ticks)

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
            val state = AxionClientState.globalModeState
            val useFastPlace = state.fastPlaceEnabled || state.replaceModeEnabled
            // Check both manual tracking (set by mixin) and vanilla key state
            val usePressed = useKeyManuallyPressed || client.options.useKey.isPressed
            val attackPressed = attackKeyManuallyPressed || client.options.attackKey.isPressed
            // Reset manual tracking - it will be set again by mixin if key is still held
            useKeyManuallyPressed = false
            attackKeyManuallyPressed = false

            if (usePressed) {
                // Reset seen targets every tick to allow continuous placement
                seenPlacementTargets.clear()

                // For infinite reach without fast place: use single-block vanilla-speed placement
                if (state.infiniteReachEnabled && !useFastPlace && !AxionToolSelectionController.isAxionSlotActive()) {
                    // Enforce vanilla placement speed (4 tick cooldown)
                    val currentTick = client.world?.time ?: 0
                    if (currentTick - lastPlacementTick >= VANILLA_PLACEMENT_COOLDOWN_TICKS) {
                        performSingleBlockPlacement(client)
                        lastPlacementTick = currentTick
                    }
                } else if (useFastPlace && !AxionToolSelectionController.isAxionSlotActive()) {
                    // Multi-sample fast place for fast place mode or infinite reach + fast place
                    // Execute every tick (0 cooldown) but only once per tick
                    val currentTick = client.world?.time ?: 0
                    val tickDiff = currentTick - lastPlacementTick

                    // Reset flag if we're on a new tick
                    if (tickDiff > 0) {
                        fastPlaceExecutedThisTick = false
                    }

                    // Only place if we haven't already placed this tick
                    if (!fastPlaceExecutedThisTick) {
                        performMultiSampleFastPlace(client)
                        lastPlacementTick = currentTick
                        fastPlaceExecutedThisTick = true
                    }
                } else if (!suppressSecondaryUntilRelease) {
                    consumeSecondaryAction(client)
                }
            }

            // Handle bulldozer mode - unified 2-tick cooldown for both IR and non-IR
            val isAxionSlotActive = AxionToolSelectionController.isAxionSlotActive()
            if (state.bulldozerEnabled && attackPressed && !isAxionSlotActive) {
                val currentTick = client.world?.time ?: 0
                if (currentTick - lastBulldozerTick >= BULLDOZER_COOLDOWN_TICKS) {
                    bypassBlockBreakingCooldown(client)
                    performSingleBulldozerBreak(client, state.infiniteReachEnabled)
                    lastBulldozerTick = currentTick
                    // Suppress vanilla attack to prevent double-breaking
                    suppressPrimaryUntilRelease = true
                    client.interactionManager?.cancelBlockBreaking()
                }
            }

            // Handle infinite reach breaking at vanilla speed in onEndTick (not just in doAttack)
            if (state.infiniteReachEnabled && !state.bulldozerEnabled && attackPressed && !AxionToolSelectionController.isAxionSlotActive()) {
                // Enforce vanilla breaking speed (4 tick cooldown)
                val currentTick = client.world?.time ?: 0
                if (currentTick - lastBreakTick >= VANILLA_BREAK_COOLDOWN_TICKS) {
                    bypassBlockBreakingCooldown(client)
                    performInfiniteReachSingleBreak(client)
                    lastBreakTick = currentTick
                }
            }

            previousAttackPressed = attackPressed
        } else {
            previousAttackPressed = false
        }

        if (!client.options.attackKey.isPressed) {
            suppressPrimaryUntilRelease = false
        } else if (suppressPrimaryUntilRelease) {
            client.interactionManager?.cancelBlockBreaking()
        }

        if (!client.options.useKey.isPressed) {
            suppressSecondaryUntilRelease = false
            seenPlacementTargets.clear()
            lastPlacementHitPos = null
            lastPlacedBlockPos = null
        }

        applyNoClip(client)
        syncRemoteNoClip(client)

        // Apply flying speed multiplier
        applyFlyingSpeed(client)
    }

    private var lastSyncedSpeedMultiplier: Float = 1.0f

    private fun applyFlyingSpeed(client: MinecraftClient) {
        val player = client.player ?: return
        if (!player.abilities.flying) {
            // Reset to vanilla speed when not flying
            player.abilities.flySpeed = 0.05f
            return
        }

        val multiplier = AxionClientState.flySpeedMultiplier

        // At 100% (default), don't apply any speed changes - let other plugins control it
        if (multiplier == 1.0f) {
            // Sync with server to clear any blessing
            syncFlightSpeedWithServer(client, multiplier)
            return
        }

        val state = AxionClientState.globalModeState

        // Safety cap: if noclip is enabled, limit speed to prevent collision issues
        val effectiveMultiplier = if (state.noClipEnabled) {
            val capped = multiplier.coerceAtMost(3.0f)
            if (capped < multiplier) {
                AxionMod.LOGGER.info("[Axion] Flying speed capped from ${(multiplier * 100).toInt()}% to 300% due to NoClip being enabled")
            }
            capped
        } else {
            multiplier
        }

        // Apply speed: vanilla base is 0.05f
        player.abilities.flySpeed = 0.05f * effectiveMultiplier

        // Sync with server to enable blessing for high speeds
        syncFlightSpeedWithServer(client, effectiveMultiplier)
    }

    private fun syncFlightSpeedWithServer(client: MinecraftClient, multiplier: Float) {
        // Only sync when connected to a server with Axion plugin
        if (client.server != null) {
            return // Single player - no need to sync
        }

        // Only sync when multiplier changes
        if (multiplier == lastSyncedSpeedMultiplier) {
            return
        }
        lastSyncedSpeedMultiplier = multiplier

        // Send to server if available
        if (AxionServerConnection.isRemoteAuthoritativeAvailable()) {
            AxionServerConnection.sendClientMessage(
                axion.protocol.FlightSpeedRequest(multiplier)
            )
        }
    }

    /**
     * Returns true when infinite reach is enabled without fast place.
     * In this case, we let vanilla handle the secondary action for continuous placement.
     */
    fun shouldLetVanillaHandleSecondaryAction(client: MinecraftClient): Boolean {
        if (!canUseModes(client)) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        val state = AxionClientState.globalModeState
        return state.infiniteReachEnabled && !state.fastPlaceEnabled && !state.replaceModeEnabled
    }

    /**
     * Returns true when infinite reach is enabled without bulldozer.
     * In this case, we let vanilla handle the primary action for continuous breaking.
     */
    fun shouldLetVanillaHandlePrimaryAction(client: MinecraftClient): Boolean {
        if (!canUseModes(client)) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        val state = AxionClientState.globalModeState
        return state.infiniteReachEnabled && !state.bulldozerEnabled
    }

    /**
     * Returns true when both infinite reach AND bulldozer are enabled.
     * In this case, we let vanilla handle the primary action for continuous multi-block breaking.
     */
    fun shouldLetVanillaHandleBulldozerInfiniteReach(client: MinecraftClient): Boolean {
        if (!canUseModes(client)) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        val state = AxionClientState.globalModeState
        return state.infiniteReachEnabled && state.bulldozerEnabled
    }

    /**
     * Handles infinite reach block breaking when vanilla's doAttack is called.
     * This enables continuous block breaking at vanilla speed.
     * Returns true if the action was handled (to cancel vanilla's handling).
     */
    fun handleInfiniteReachBreaking(client: MinecraftClient): Boolean {
        val state = AxionClientState.globalModeState

        // Only handle if infinite reach is enabled and bulldozer is NOT enabled
        if (!state.infiniteReachEnabled || state.bulldozerEnabled) {
            return false
        }

        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }

        // Enforce vanilla breaking speed (4 tick cooldown)
        val currentTick = client.world?.time ?: 0
        if (currentTick - lastBreakTick < VANILLA_BREAK_COOLDOWN_TICKS) {
            return true // Still "handle" it to cancel vanilla, but don't break
        }

        // Perform the break
        val player = client.player ?: return false
        val world = client.world ?: return false
        val cameraEntity = client.cameraEntity ?: player
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val direction = cameraEntity.getRotationVec(1.0f)
        val maxDistance = AxionTargeting.DEFAULT_REACH

        val target = origin.add(direction.multiply(maxDistance))
        val hit = world.raycast(
            RaycastContext(
                origin,
                target,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                cameraEntity,
            ),
        )

        if (hit.type != HitResult.Type.BLOCK) {
            return true // Handle but no block hit
        }

        val blockHit = hit as BlockHitResult
        val targetPos = blockHit.blockPos.toImmutable()
        val brokenState = world.getBlockState(targetPos)

        if (brokenState.isAir) {
            return true // Handle but already air
        }

        // Bypass block breaking cooldown
        bypassBlockBreakingCooldown(client)

        // Dispatch break operation
        dispatcher.dispatch(
            ClearRegionOperation(
                BlockRegion(targetPos, targetPos),
            ),
        )
        SymmetryBreakController.dispatchDerivedBreaks(client, targetPos)
        player.swingHand(Hand.MAIN_HAND)
        if (!brokenState.isAir) {
            playBreakEffects(client, targetPos, brokenState)
        }

        lastBreakTick = currentTick
        return true
    }

    /**
     * Performs single block breaking for infinite reach at vanilla speed.
     * Used by onEndTick for continuous breaking.
     */
    private fun performInfiniteReachSingleBreak(client: MinecraftClient) {
        val player = client.player ?: return
        val world = client.world ?: return
        val cameraEntity = client.cameraEntity ?: player
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val direction = cameraEntity.getRotationVec(1.0f)
        val maxDistance = AxionTargeting.DEFAULT_REACH

        val target = origin.add(direction.multiply(maxDistance))
        val hit = world.raycast(
            RaycastContext(
                origin,
                target,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                cameraEntity,
            ),
        )

        if (hit.type != HitResult.Type.BLOCK) {
            return
        }

        val blockHit = hit as BlockHitResult
        val targetPos = blockHit.blockPos.toImmutable()
        val brokenState = world.getBlockState(targetPos)

        if (brokenState.isAir) {
            return
        }

        bypassBlockBreakingCooldown(client)
        dispatcher.dispatch(
            ClearRegionOperation(
                BlockRegion(targetPos, targetPos),
            ),
        )
        SymmetryBreakController.dispatchDerivedBreaks(client, targetPos)
        player.swingHand(Hand.MAIN_HAND)
        if (!brokenState.isAir) {
            playBreakEffects(client, targetPos, brokenState)
        }
    }

    /**
     * Handles bulldozer + infinite reach multi-block breaking when vanilla's doAttack is called.
     * This breaks multiple blocks along the ray at fast speed.
     * Returns true if the action was handled (to cancel vanilla's handling).
     */
    fun handleBulldozerInfiniteReachBreaking(client: MinecraftClient): Boolean {
        val state = AxionClientState.globalModeState

        // Only handle if both infinite reach AND bulldozer are enabled
        if (!state.infiniteReachEnabled || !state.bulldozerEnabled) {
            return false
        }

        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }

        // Unified cooldown with regular bulldozer - 2 tick speed
        val currentTick = client.world?.time ?: 0
        if (currentTick - lastBulldozerTick < BULLDOZER_COOLDOWN_TICKS) {
            return false
        }

        bypassBlockBreakingCooldown(client)
        performSingleBulldozerBreak(client, infiniteReach = true)
        lastBulldozerTick = currentTick

        return true
    }

    /**
     * Handles infinite reach placement when vanilla's doItemUse is called.
     * This bypasses vanilla's item use cooldown to enable continuous placement.
     * Returns true if the action was handled (to cancel vanilla's handling).
     */
    fun handleInfiniteReachPlacement(client: MinecraftClient): Boolean {
        val state = AxionClientState.globalModeState
        val useFastPlace = state.fastPlaceEnabled || state.replaceModeEnabled

        // Only handle if infinite reach is enabled and fast place is NOT enabled
        if (!state.infiniteReachEnabled || useFastPlace) {
            return false
        }

        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }

        // Allow usable items (potions, shields, food, etc.) to work normally
        val player = client.player
        val heldStack = player?.mainHandStack
        val item = heldStack?.item
        if (item != null && player != null) {
            // Check if item has a use action (food, potions, shields, etc.)
            // Items with maxUseTime > 0 are usable (food, potions, shields, bows, etc.)
            if (item.getMaxUseTime(heldStack, player) > 0) {
                // Let vanilla handle items with right-click actions (potions, shields, etc.)
                return false
            }
        }

        // Enforce vanilla placement speed (4 tick cooldown)
        val currentTick = client.world?.time ?: 0
        val tickDiff = currentTick - lastPlacementTick
        if (tickDiff < VANILLA_PLACEMENT_COOLDOWN_TICKS) {
            return true // Still "handle" it to cancel vanilla, but don't place
        }

        // Bypass item use cooldown
        bypassItemUseCooldown(client)

        // Perform the placement
        performSingleBlockPlacement(client)
        lastPlacementTick = currentTick

        return true
    }

    /**
     * Handles fast place + infinite reach multi-block placement when vanilla's doItemUse is called.
     * This places multiple blocks along the ray at fast speed.
     * Returns true if the action was handled (to cancel vanilla's handling).
     */
    fun handleFastPlaceInfiniteReachPlacement(client: MinecraftClient): Boolean {
        val state = AxionClientState.globalModeState
        val useFastPlace = state.fastPlaceEnabled || state.replaceModeEnabled

        // Only handle if both infinite reach AND fast place are enabled
        if (!state.infiniteReachEnabled || !useFastPlace) {
            return false
        }

        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }

        // Execute every tick (0 cooldown) but only once per tick
        val currentTick = client.world?.time ?: 0
        val tickDiff = currentTick - lastPlacementTick

        // Reset flag if we're on a new tick
        if (tickDiff > 0) {
            fastPlaceExecutedThisTick = false
        }

        // If already placed this tick, let vanilla handle it
        if (fastPlaceExecutedThisTick) {
            return false
        }

        // Use multi-sample fast place like regular fast place
        bypassItemUseCooldown(client)
        performMultiSampleFastPlace(client)
        lastPlacementTick = currentTick
        fastPlaceExecutedThisTick = true

        return true
    }

    /**
     * Called by the mixin when doItemUse is triggered to manually track use key state.
     * This is needed because cancelling doItemUse prevents Minecraft from updating key bindings.
     */
    fun setUseKeyManuallyPressed() {
        useKeyManuallyPressed = true
    }

    /**
     * Called by the mixin when doAttack is triggered to manually track attack key state.
     * This is needed because cancelling doAttack prevents Minecraft from updating key bindings.
     */
    fun setAttackKeyManuallyPressed() {
        attackKeyManuallyPressed = true
    }

    /**
     * Check if fast place mode is enabled (fastPlace or replaceMode)
     */
    fun isFastPlaceEnabled(client: MinecraftClient): Boolean {
        val state = AxionClientState.globalModeState
        return state.fastPlaceEnabled || state.replaceModeEnabled
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

        while (AxionKeybindings.toggleBulldozer.wasPressed()) {
            toggleBulldozer(client)
        }

        while (AxionKeybindings.toggleFastPlace.wasPressed()) {
            toggleFastPlace(client)
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
        if (AxionClientState.globalModeState.bulldozerEnabled) {
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

        val player = client.player ?: return false
        val world = client.world ?: return false
        val cameraEntity = client.cameraEntity ?: player
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val direction = cameraEntity.getRotationVec(1.0f)
        val maxDistance = if (state.infiniteReachEnabled) AxionTargeting.DEFAULT_REACH else player.blockInteractionRange

        // For infinite reach placement:
        // - Within vanilla range: use interactBlock for client prediction
        // - Beyond vanilla range: use dispatch for server-side placement
        if (state.infiniteReachEnabled && !state.replaceModeEnabled) {
            val target = origin.add(direction.multiply(maxDistance))
            val hit = world.raycast(
                RaycastContext(
                    origin,
                    target,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    cameraEntity,
                ),
            )

            if (hit.type == HitResult.Type.BLOCK) {
                val blockHit = hit as BlockHitResult
                val beyondVanillaReach = origin.squaredDistanceTo(hit.pos) > (player.blockInteractionRange * player.blockInteractionRange)

                bypassItemUseCooldown(client)

                if (!beyondVanillaReach) {
                    // Within vanilla range - use vanilla interaction for client prediction
                    client.interactionManager?.interactBlock(player, Hand.MAIN_HAND, blockHit)
                    client.player?.swingHand(Hand.MAIN_HAND)
                } else {
                    // Beyond vanilla range - use dispatch for server-side placement
                    val blockTarget = ModeTargeting.BlockTarget(
                        hitResult = blockHit,
                        squaredDistance = origin.squaredDistanceTo(hit.pos),
                        beyondVanillaReach = true,
                    )

                    val operation = BuildPlacementService.createPlacementOperation(
                        client = client,
                        target = blockTarget,
                        symmetryConfig = ActiveSymmetryConfig.current()
                            ?.takeIf(ActiveSymmetryConfig::hasDerivedTransforms),
                        replaceMode = false,
                    )
                    if (operation != null) {
                        dispatcher.dispatch(operation)
                        client.player?.swingHand(Hand.MAIN_HAND)
                        playPlacementEffects(client, operation)
                    }
                }
                return true
            }
            return false
        }

        // For replace mode or vanilla reach, use single raycast
        val target = origin.add(direction.multiply(maxDistance))
        val hit = world.raycast(
            RaycastContext(
                origin,
                target,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                cameraEntity,
            ),
        )

        if (hit.type != HitResult.Type.BLOCK) {
            return false
        }

        val blockHit = hit as BlockHitResult
        val beyondVanillaReach = origin.squaredDistanceTo(hit.pos) > (player.blockInteractionRange * player.blockInteractionRange)

        if (!state.infiniteReachEnabled && beyondVanillaReach) {
            return false
        }
        if (!state.replaceModeEnabled && !beyondVanillaReach) {
            return false
        }

        val blockTarget = ModeTargeting.BlockTarget(
            hitResult = blockHit,
            squaredDistance = origin.squaredDistanceTo(hit.pos),
            beyondVanillaReach = beyondVanillaReach,
        )

        val operation = BuildPlacementService.createPlacementOperation(
            client = client,
            target = blockTarget,
            symmetryConfig = ActiveSymmetryConfig.current()
                ?.takeIf(ActiveSymmetryConfig::hasDerivedTransforms),
            replaceMode = state.replaceModeEnabled,
        ) ?: return false
        bypassItemUseCooldown(client)
        dispatcher.dispatch(operation)
        client.player?.swingHand(Hand.MAIN_HAND)
        playPlacementEffects(client, operation)
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
            primeNoClipEscapeAssist(client)
        } else {
            noClipEscapeTicks = 0
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

    private fun toggleBulldozer(client: MinecraftClient) {
        val nextState = AxionClientState.globalModeState.copy(
            bulldozerEnabled = !AxionClientState.globalModeState.bulldozerEnabled,
        )
        AxionClientState.updateGlobalModes(nextState)
        showToast(client, "Bulldozer", nextState.bulldozerEnabled)
    }

    private fun toggleFastPlace(client: MinecraftClient) {
        val nextState = AxionClientState.globalModeState.copy(
            fastPlaceEnabled = !AxionClientState.globalModeState.fastPlaceEnabled,
        )
        AxionClientState.updateGlobalModes(nextState)
        showToast(client, "Fast Place", nextState.fastPlaceEnabled)
    }

    private fun applyNoClip(client: MinecraftClient) {
        val player = client.player ?: return
        val active = AxionClientState.globalModeState.noClipEnabled
        if (!active) {
            noClipEscapeTicks = 0
        } else if (!player.abilities.flying && isInsideSolidBlock(player)) {
            // Player is suffocating inside blocks — force flight and prime escape assist
            noClipEscapeTicks = NO_CLIP_ESCAPE_TICKS
            player.abilities.flying = true
        }
        val escapeAssist = active && noClipEscapeTicks > 0
        if (escapeAssist) {
            noClipEscapeTicks -= 1
        }
        // NoClip only takes effect when flying (or during escape assist).
        // When the player stops flying, collisions resume normally — no rubberbanding.
        val shouldNoClip = active && (player.abilities.flying || escapeAssist)
        player.noClip = player.isSpectator || shouldNoClip
        player.setNoGravity(player.isSpectator || player.abilities.flying || escapeAssist)
        if (shouldNoClip) {
            player.setOnGround(false)
            player.horizontalCollision = false
            player.verticalCollision = false
        }
        client.server
            ?.playerManager
            ?.getPlayer(player.uuid)
            ?.let { serverPlayer ->
                val serverShouldNoClip = active && (serverPlayer.abilities.flying || escapeAssist)
                serverPlayer.noClip = serverPlayer.isSpectator || serverShouldNoClip
                serverPlayer.setNoGravity(serverPlayer.isSpectator || serverPlayer.abilities.flying || escapeAssist)
                if (serverShouldNoClip) {
                    serverPlayer.setOnGround(false)
                    serverPlayer.horizontalCollision = false
                    serverPlayer.verticalCollision = false
                }
            }
    }

    private fun primeNoClipEscapeAssist(client: MinecraftClient) {
        val player = client.player ?: return
        if (!player.abilities.flying && isInsideSolidBlock(player)) {
            noClipEscapeTicks = NO_CLIP_ESCAPE_TICKS
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
        // NoClip only active when flying or during escape assist
        return playerEntity.abilities.flying || noClipEscapeTicks > 0
    }

    private fun isInsideSolidBlock(player: PlayerEntity): Boolean {
        val world = MinecraftClient.getInstance().world ?: return false
        val bounds = player.boundingBox.contract(1.0E-4)
        return world.getBlockCollisions(player, bounds).iterator().hasNext()
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
        val currentTick = client.world?.time ?: 0

        // Throttle placement sounds to prevent spam during fast place mode
        if (currentTick - lastPlacementSoundTick < PLACEMENT_SOUND_COOLDOWN_TICKS) {
            return
        }
        lastPlacementSoundTick = currentTick

        // Play sound only for the first placement (represents the batch)
        val placement = operation.placements.firstOrNull() ?: return
        val soundGroup = placement.state.soundGroup
        VersionCompatImpl.playSoundClient(
            world,
            placement.pos.x + 0.5,
            placement.pos.y + 0.5,
            placement.pos.z + 0.5,
            soundGroup.placeSound,
            SoundCategory.BLOCKS,
            (soundGroup.volume + 1.0f) / 2.0f,
            soundGroup.pitch * 0.8f,
        )
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

            val inventorySlot = findInventorySlot(inventory, pickedItem, PlayerInventory.getHotbarSize() until VersionCompatImpl.getMainInventoryStacks(inventory).size)
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

    private fun bypassItemUseCooldown(client: MinecraftClient) {
        (client as MinecraftClientAccessor).axionSetItemUseCooldown(0)
    }

    private fun bypassBlockBreakingCooldown(client: MinecraftClient) {
        val interactionManager = client.interactionManager ?: return
        (interactionManager as ClientPlayerInteractionManagerAccessor).axionSetBlockBreakingCooldown(0)
    }

    private fun performMultiSampleFastPlace(client: MinecraftClient) {
        val player = client.player ?: return
        val world = client.world ?: return
        val cameraEntity = client.cameraEntity ?: player
        val state = AxionClientState.globalModeState
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val direction = cameraEntity.getRotationVec(1.0f)
        val maxDistance = if (state.infiniteReachEnabled) AxionTargeting.DEFAULT_REACH else player.blockInteractionRange

        // For infinite reach, use ray marching to find multiple blocks along the ray
        if (state.infiniteReachEnabled) {
            val seenTargets = linkedSetOf<PlacementSampleTarget>()
            val withinRangeOperations = mutableListOf<BlockHitResult>()
            val beyondRangeOperations = mutableListOf<axion.common.operation.EditOperation>()
            val vanillaReachSq = player.blockInteractionRange * player.blockInteractionRange

            // Ray marching to find blocks along the line
            val stepSize = 0.3
            var currentPos = origin
            var steps = 0
            var blocksFound = 0
            val maxBlocks = 25
            val maxSteps = (maxDistance / stepSize).toInt()

            while (steps < maxSteps && blocksFound < maxBlocks) {
                currentPos = currentPos.add(direction.multiply(stepSize))
                steps++

                val blockPos = BlockPos.ofFloored(currentPos)
                val hitState = world.getBlockState(blockPos)

                if (state.replaceModeEnabled) {
                    // For replace mode: find non-air blocks to replace
                    if (hitState.isAir) {
                        continue
                    }
                } else {
                    // For fast place: find solid blocks to place against
                    if (hitState.isAir || !hitState.isSolid) {
                        continue
                    }
                }

                blocksFound++

                // Use consistent side based on look direction
                val side = Direction.getFacing(direction.x, direction.y, direction.z).opposite

                // Calculate quantized hit offset for deduplication
                val localHit = currentPos.subtract(Vec3d.ofCenter(blockPos))
                val hitOffset = Vec3i(
                    (localHit.x * 4).toInt(),
                    (localHit.y * 4).toInt(),
                    (localHit.z * 4).toInt()
                )
                val sampleTarget = PlacementSampleTarget(blockPos, side, hitOffset)

                if (!seenTargets.add(sampleTarget)) {
                    continue
                }

                // Need to raycast to get proper hit result
                val hit = world.raycast(
                    RaycastContext(
                        origin,
                        currentPos,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        cameraEntity,
                    ),
                )

                if (hit.type != HitResult.Type.BLOCK) {
                    continue
                }

                val blockHit = hit as BlockHitResult
                val beyondVanillaReach = origin.squaredDistanceTo(currentPos) > vanillaReachSq

                if (!beyondVanillaReach) {
                    withinRangeOperations += blockHit
                } else {
                    val blockTarget = ModeTargeting.BlockTarget(
                        hitResult = blockHit,
                        squaredDistance = origin.squaredDistanceTo(currentPos),
                        beyondVanillaReach = true,
                    )
                    val operation = BuildPlacementService.createPlacementOperation(
                        client = client,
                        target = blockTarget,
                        symmetryConfig = ActiveSymmetryConfig.current()?.takeIf(ActiveSymmetryConfig::hasDerivedTransforms),
                        replaceMode = state.replaceModeEnabled,
                    )
                    if (operation != null) {
                        beyondRangeOperations += operation
                    }
                }
            }

            bypassItemUseCooldown(client)

            // Execute within-range placements with interactBlock
            withinRangeOperations.forEach { blockHit ->
                client.interactionManager?.interactBlock(player, Hand.MAIN_HAND, blockHit)
            }

            // Execute beyond-range placements with dispatch
            if (beyondRangeOperations.isNotEmpty()) {
                dispatchBatch(beyondRangeOperations)
                beyondRangeOperations.forEach { operation ->
                    if (operation is axion.common.operation.SymmetryPlacementOperation) {
                        playPlacementEffects(client, operation)
                    }
                }
            }

            if (withinRangeOperations.isNotEmpty() || beyondRangeOperations.isNotEmpty()) {
                client.player?.swingHand(Hand.MAIN_HAND)
            }
            return
        }

        val operations = mutableListOf<axion.common.operation.EditOperation>()

        if (state.replaceModeEnabled) {
            // For replace mode, use iterative raycasting to find blocks along the ray
            var rayOrigin = origin
            var blocksFound = 0

            while (blocksFound < MULTI_SAMPLE_COUNT) {
                val rayTarget = rayOrigin.add(direction.multiply(maxDistance))
                val hit = world.raycast(
                    RaycastContext(
                        rayOrigin,
                        rayTarget,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        cameraEntity,
                    ),
                )

                if (hit.type != HitResult.Type.BLOCK) {
                    break
                }

                val blockHit = hit as BlockHitResult
                val sampleTarget = PlacementSampleTarget(blockHit.blockPos.toImmutable(), blockHit.side)
                if (!seenPlacementTargets.add(sampleTarget)) {
                    break
                }

                val blockTarget = ModeTargeting.BlockTarget(
                    hitResult = blockHit,
                    squaredDistance = origin.squaredDistanceTo(hit.pos),
                    beyondVanillaReach = origin.squaredDistanceTo(hit.pos) > (player.blockInteractionRange * player.blockInteractionRange),
                )

                val operation = BuildPlacementService.createPlacementOperation(
                    client = client,
                    target = blockTarget,
                    symmetryConfig = ActiveSymmetryConfig.current()?.takeIf(ActiveSymmetryConfig::hasDerivedTransforms),
                    replaceMode = true,
                )
                if (operation != null) {
                    operations += operation
                    blocksFound++
                }

                // Move ray origin slightly past the hit point to find the next block
                rayOrigin = hit.pos.add(direction.multiply(0.1))
            }
        } else {
            // For normal fast place, always use COLLIDER to find multiple blocks to place against
            for (i in 0 until MULTI_SAMPLE_COUNT) {
                val t = (i + 1).toDouble() / MULTI_SAMPLE_COUNT.toDouble()
                val target = origin.add(direction.multiply(maxDistance * t))
                val hit = world.raycast(
                    RaycastContext(
                        origin,
                        target,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        cameraEntity,
                    ),
                )

                if (hit.type == HitResult.Type.BLOCK) {
                    val blockHit = hit as BlockHitResult
                    val sampleTarget = PlacementSampleTarget(blockHit.blockPos.toImmutable(), blockHit.side)
                    if (!seenPlacementTargets.add(sampleTarget)) {
                        continue
                    }
                    val blockTarget = ModeTargeting.BlockTarget(
                        hitResult = blockHit,
                        squaredDistance = origin.squaredDistanceTo(hit.pos),
                        beyondVanillaReach = origin.squaredDistanceTo(hit.pos) > (player.blockInteractionRange * player.blockInteractionRange),
                    )

                    val operation = BuildPlacementService.createPlacementOperation(
                        client = client,
                        target = blockTarget,
                        symmetryConfig = ActiveSymmetryConfig.current()?.takeIf(ActiveSymmetryConfig::hasDerivedTransforms),
                        replaceMode = false,
                    )
                    if (operation != null) {
                        operations += operation
                    }
                }
            }
        }

        if (operations.isEmpty()) {
            return
        }

        bypassItemUseCooldown(client)
        dispatchBatch(operations)
        client.player?.swingHand(Hand.MAIN_HAND)
        operations.forEach { operation ->
            if (operation is axion.common.operation.SymmetryPlacementOperation) {
                playPlacementEffects(client, operation)
            }
        }
    }

    private fun performSingleBlockPlacement(client: MinecraftClient) {
        val player = client.player ?: return
        val world = client.world ?: return
        val cameraEntity = client.cameraEntity ?: player
        val state = AxionClientState.globalModeState
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val direction = cameraEntity.getRotationVec(1.0f)
        val vanillaReach = player.blockInteractionRange
        val maxDistance = if (state.infiniteReachEnabled) AxionTargeting.DEFAULT_REACH else vanillaReach

        bypassItemUseCooldown(client)

        // Iterative raycasting to find the first valid placement surface
        var rayOrigin = origin
        val seenPositions = mutableSetOf<BlockPos>()

        while (seenPositions.size < 20) { // Limit iterations to prevent infinite loops
            val target = rayOrigin.add(direction.multiply(maxDistance))
            val hit = world.raycast(
                RaycastContext(
                    rayOrigin,
                    target,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    cameraEntity,
                ),
            )

            if (hit.type != HitResult.Type.BLOCK) {
                return
            }

            val blockHit = hit as BlockHitResult
            val hitPos = blockHit.blockPos

            // Check if we've already tried this position
            if (!seenPositions.add(hitPos)) {
                rayOrigin = hit.pos.add(direction.multiply(0.1))
                continue
            }

            val hitDistanceSq = origin.squaredDistanceTo(hit.pos)
            val blockTarget = ModeTargeting.BlockTarget(
                hitResult = blockHit,
                squaredDistance = hitDistanceSq,
                beyondVanillaReach = hitDistanceSq > (vanillaReach * vanillaReach),
            )

            val operation = BuildPlacementService.createPlacementOperation(
                client = client,
                target = blockTarget,
                symmetryConfig = ActiveSymmetryConfig.current()
                    ?.takeIf(ActiveSymmetryConfig::hasDerivedTransforms),
                replaceMode = false,
            )

            if (operation != null) {
                dispatcher.dispatch(operation)
                player.swingHand(Hand.MAIN_HAND)
                playPlacementEffects(client, operation)
                return
            }

            rayOrigin = hit.pos.add(direction.multiply(0.1))
        }
    }

    private fun performSingleBulldozerBreak(
        client: MinecraftClient,
        infiniteReach: Boolean,
    ) {
        val player = client.player ?: return
        val world = client.world ?: return
        val cameraEntity = client.cameraEntity ?: player
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val direction = cameraEntity.getRotationVec(1.0f)
        val vanillaReach = player.blockInteractionRange
        val maxDistance = if (infiniteReach) AxionTargeting.DEFAULT_REACH else vanillaReach

        // Raycast to find target block
        val hit = world.raycast(
            RaycastContext(
                origin,
                origin.add(direction.multiply(maxDistance)),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                cameraEntity,
            ),
        )

        if (hit.type != HitResult.Type.BLOCK) {
            return
        }

        val blockHit = hit as BlockHitResult
        val targetPos = blockHit.blockPos
        val brokenState = world.getBlockState(targetPos)

        if (brokenState.isAir || brokenState.block is net.minecraft.block.FluidBlock) {
            return
        }

        val distSq = origin.squaredDistanceTo(Vec3d.ofCenter(targetPos))
        val beyondVanillaReach = distSq > (vanillaReach * vanillaReach)

        if (!beyondVanillaReach) {
            // Within vanilla range - use vanilla attackBlock for proper client prediction
            // This prevents ghost blocks by letting the client handle the break prediction
            client.interactionManager?.attackBlock(targetPos, blockHit.side)
        } else {
            // Beyond vanilla range - use dispatch for infinite reach breaking
            dispatcher.dispatch(
                ClearRegionOperation(
                    BlockRegion(targetPos, targetPos),
                ),
            )
            SymmetryBreakController.dispatchDerivedBreaks(client, targetPos)
            playBreakEffects(client, targetPos, brokenState)
        }

        player.swingHand(Hand.MAIN_HAND)
    }

    private fun dispatchBatch(operations: List<axion.common.operation.EditOperation>) {
        when (operations.size) {
            0 -> return
            1 -> dispatcher.dispatch(operations.first())
            else -> dispatcher.dispatch(axion.common.operation.CompositeOperation(operations))
        }
    }

    private data class PlacementSampleTarget(
        val pos: net.minecraft.util.math.BlockPos,
        val side: net.minecraft.util.math.Direction,
        val hitOffset: net.minecraft.util.math.Vec3i? = null,
    )
}

package axion.client.symmetry

import axion.client.AxionClientState
import axion.client.compat.VersionCompatImpl
import axion.client.mode.AxionCapabilityPolicy
import axion.client.mode.BuildPlacementService
import axion.client.mode.InfiniteReachInteractionPolicy
import axion.client.mode.ModeTargeting
import axion.client.tool.AxionToolSelectionController
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.SymmetryConfig
import axion.common.model.SymmetryState
import axion.protocol.AxionInteractionOrigin
import net.minecraft.client.MinecraftClient
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand

object SymmetryPlacementController {
    private val dispatcher = SymmetryAwareOperationDispatcher(
        suppressBlockUpdates = AxionCapabilityPolicy::suppressBlockUpdates,
    )
    private val infiniteReachDispatcher = SymmetryAwareOperationDispatcher(
        interactionOrigin = AxionInteractionOrigin.INFINITE_REACH,
        suppressBlockUpdates = AxionCapabilityPolicy::suppressBlockUpdates,
    )

    fun updatePreview(client: MinecraftClient): Boolean {
        AxionClientState.updateSymmetryPreview(null)
        return false
    }

    fun handleUse(client: MinecraftClient): Boolean {
        if (!AxionToolSelectionController.isCreativeModeAllowed()) {
            return false
        }
        val config = currentConfig() ?: return false
        if (!ActiveSymmetryConfig.hasDerivedTransforms(config)) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        // Don't consume when fast place or replace mode is enabled - let multi-sample path handle it
        val state = AxionClientState.globalModeState
        if (state.fastPlaceEnabled || state.replaceModeEnabled) {
            return false
        }
        // Infinite reach is an extension beyond vanilla's target range. If
        // vanilla already has a block/entity target, leave the click untouched
        // so containers, buttons, doors, and ordinary placement keep working.
        if (InfiniteReachInteractionPolicy.shouldYieldToVanilla(
                infiniteReachEnabled = state.infiniteReachEnabled,
                replaceModeEnabled = false,
                vanillaTargetPresent = client.crosshairTarget?.type?.name?.let { it != "MISS" } == true,
                axionOwnsPlacement = state.forcePlaceEnabled || state.noUpdatesEnabled,
            )
        ) {
            return false
        }
        val target = ModeTargeting.currentBlockTarget(client) ?: return false
        val operation = BuildPlacementService.createPlacementOperation(
            client = client,
            target = target,
            symmetryConfig = config,
            replaceMode = state.replaceModeEnabled,
        )
        if (operation == null || operation.placements.size <= 1) {
            return false
        }

        val interactionOrigin = SymmetryPlacementOriginPolicy.forTarget(
            infiniteReachEnabled = state.infiniteReachEnabled,
            beyondVanillaReach = target.beyondVanillaReach,
        )
        when (interactionOrigin) {
            AxionInteractionOrigin.NONE -> dispatcher
            AxionInteractionOrigin.INFINITE_REACH -> infiniteReachDispatcher
        }.dispatch(operation)
        playPlacementEffects(client, operation)
        client.player?.swingHand(Hand.MAIN_HAND)
        return true
    }

    private fun currentConfig(): SymmetryConfig? {
        return when (val state = AxionClientState.symmetryState) {
            SymmetryState.Inactive -> null
            is SymmetryState.Active -> state.config
        }
    }

    private fun playPlacementEffects(
        client: MinecraftClient,
        operation: axion.common.operation.SymmetryPlacementOperation,
    ) {
        val world = client.world ?: return
        operation.placements.forEach { placement ->
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
    }
}

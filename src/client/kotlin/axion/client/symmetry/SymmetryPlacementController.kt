package axion.client.symmetry

import axion.client.AxionClientState
import axion.client.compat.VersionCompatImpl
import axion.client.mode.BuildPlacementService
import axion.client.mode.ModeTargeting
import axion.client.tool.AxionToolSelectionController
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.SymmetryConfig
import axion.common.model.SymmetryState
import net.minecraft.client.MinecraftClient
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand

object SymmetryPlacementController {
    private val dispatcher = SymmetryAwareOperationDispatcher()

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

        dispatcher.dispatch(operation)
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

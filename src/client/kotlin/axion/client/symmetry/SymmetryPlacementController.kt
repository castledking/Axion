package axion.client.symmetry

import axion.client.AxionClientState
import axion.client.mode.BuildPlacementService
import axion.client.mode.ModeTargeting
import axion.client.tool.AxionToolSelectionController
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.SymmetryConfig
import axion.common.model.SymmetryState
import net.minecraft.client.MinecraftClient
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
        val target = ModeTargeting.currentBlockTarget(client) ?: return false
        val operation = BuildPlacementService.createPlacementOperation(
            client = client,
            target = target,
            symmetryConfig = config,
            replaceMode = AxionClientState.globalModeState.replaceModeEnabled,
        )
        if (operation == null || operation.placements.size <= 1) {
            return false
        }

        dispatcher.dispatch(operation)
        client.player?.swingHand(Hand.MAIN_HAND)
        return true
    }

    private fun currentConfig(): SymmetryConfig? {
        return when (val state = AxionClientState.symmetryState) {
            SymmetryState.Inactive -> null
            is SymmetryState.Active -> state.config
        }
    }
}

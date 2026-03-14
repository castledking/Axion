package axion.client.symmetry

import axion.client.AxionClientState
import axion.client.network.AxionServerConnection
import axion.client.network.LocalOperationDispatcher
import axion.client.tool.AxionToolSelectionController
import axion.common.model.SymmetryConfig
import axion.common.model.SymmetryState
import axion.common.operation.SymmetryBlockPlacement
import axion.common.operation.SymmetryPlacementOperation
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Hand

object SymmetryPlacementController {
    private val dispatcher = LocalOperationDispatcher()

    fun updatePreview(client: MinecraftClient): Boolean {
        val config = currentConfig() ?: return false
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }

        val placement = SymmetryPlacementService.createPlacementResult(client, config) ?: return false
        AxionClientState.updateSymmetryPreview(
            SymmetryPreviewState(
                anchor = config.anchor,
                sourceBlock = placement.primaryPlacement.pos,
                transformedBlocks = placement.derivedPlacements.map { it.pos },
            ),
        )
        return true
    }

    fun handleUse(client: MinecraftClient): Boolean {
        val config = currentConfig() ?: return false
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        if (client.server == null) {
            AxionServerConnection.notifyPlayerOnce("Symmetry placement is not server-backed yet.")
            return false
        }

        val player = client.player ?: return false
        val world = client.world ?: return false
        val interactionManager = client.interactionManager ?: return false
        val placement = SymmetryPlacementService.createPlacementResult(client, config, Hand.MAIN_HAND) ?: return false
        val beforeState = world.getBlockState(placement.primaryPlacement.pos)
        val actionResult = interactionManager.interactBlock(player, Hand.MAIN_HAND, placement.hitResult)
        if (!actionResult.isAccepted()) {
            return true
        }

        val afterState = world.getBlockState(placement.primaryPlacement.pos)
        if (afterState != placement.primaryPlacement.state || afterState == beforeState) {
            return true
        }

        if (placement.derivedPlacements.isNotEmpty()) {
            dispatcher.dispatch(
                SymmetryPlacementOperation(
                    placements = placement.derivedPlacements.map { derived ->
                        SymmetryBlockPlacement(derived.pos, derived.state)
                    },
                ),
            )
        }

        return true
    }

    private fun currentConfig(): SymmetryConfig? {
        return when (val state = AxionClientState.symmetryState) {
            SymmetryState.Inactive -> null
            is SymmetryState.Active -> state.config
        }
    }
}

package axion.client.selection

import axion.client.AxionClientState
import axion.client.tool.AxionToolSelectionController
import axion.common.model.BlockRegion
import axion.common.model.SelectionState
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos

object SelectionController {
    private var currentTarget: AxionTarget = AxionTarget.MissTarget

    fun onEndTick(client: MinecraftClient) {
        currentTarget = if (AxionToolSelectionController.isAxionSlotActive()) {
            SelectionRaycast.raycast(client)
        } else {
            AxionTarget.MissTarget
        }
    }

    fun currentTarget(): AxionTarget = currentTarget

    fun currentRegion(): BlockRegion? = when (val state = AxionClientState.selectionState) {
        SelectionState.Idle -> null
        is SelectionState.FirstCornerSet -> BlockRegion(state.firstCorner, state.firstCorner)
        is SelectionState.RegionDefined -> state.region()
    }

    fun clear() {
        AxionClientState.updateSelection(SelectionState.Idle)
    }

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        if (!isRegionSelectionContext()) {
            return false
        }

        val blockPos = currentTarget.blockPosOrNull() ?: return false
        AxionClientState.updateSelection(SelectionState.FirstCornerSet(blockPos.toImmutable()))
        return true
    }

    fun handleSecondaryAction(client: MinecraftClient): Boolean {
        if (!isRegionSelectionContext()) {
            return false
        }

        val blockPos = currentTarget.blockPosOrNull() ?: return false
        val nextState = when (val state = AxionClientState.selectionState) {
            SelectionState.Idle -> return false
            is SelectionState.FirstCornerSet -> SelectionState.RegionDefined(
                firstCorner = state.firstCorner,
                secondCorner = blockPos.toImmutable(),
            )

            is SelectionState.RegionDefined -> state.copy(secondCorner = blockPos.toImmutable())
        }

        AxionClientState.updateSelection(nextState)
        return true
    }

    fun selectionAnchor(): BlockPos? = when (val state = AxionClientState.selectionState) {
        SelectionState.Idle -> null
        is SelectionState.FirstCornerSet -> state.firstCorner
        is SelectionState.RegionDefined -> state.firstCorner
    }

    fun expandRegionToCurrentTarget(region: BlockRegion): BlockRegion? {
        val targetBlock = currentTarget.blockPosOrNull()?.toImmutable() ?: return null
        val hitPos = currentTarget.hitPosOrNull() ?: return null
        val face = SelectionBounds.pickFace(region, hitPos)
        return region.expandFace(face, targetBlock)
    }

    private fun isRegionSelectionContext(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive() &&
            AxionToolSelectionController.selectedSubtool().usesRegionSelection
    }
}

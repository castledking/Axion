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
        val modeActive = AxionClientState.globalModeState.infiniteReachEnabled
        currentTarget = if (AxionToolSelectionController.isAxionSlotActive() || modeActive) {
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

    fun expandRegionToCurrentTarget(client: MinecraftClient, region: BlockRegion): BlockRegion? {
        val targetBlock = currentTarget.blockPosOrNull()?.toImmutable()
        val targetHitPos = currentTarget.hitPosOrNull()
        if (targetBlock != null && targetHitPos != null) {
            val outwardFace = SelectionBounds.outwardFaceToward(region, targetBlock, targetHitPos)
            if (outwardFace != null) {
                return region.expandFace(outwardFace, targetBlock)
            }
        }

        val cameraEntity = client.cameraEntity ?: client.player ?: return null
        val faceHit = SelectionBounds.raycastFace(
            region = region,
            origin = cameraEntity.getCameraPosVec(1.0f),
            direction = cameraEntity.getRotationVec(1.0f),
            maxDistance = AxionTargeting.DEFAULT_REACH,
        ) ?: currentTarget.hitPosOrNull()?.let { hitPos ->
            SelectionBounds.FaceHit(
                face = SelectionBounds.pickFace(region, hitPos),
                point = hitPos,
            )
        } ?: return null

        return if (targetBlock != null && isBeyondFace(region, faceHit.face, targetBlock)) {
            region.expandFace(faceHit.face, targetBlock)
        } else {
            region.extendFace(faceHit.face)
        }
    }

    private fun isRegionSelectionContext(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive() &&
            AxionToolSelectionController.selectedSubtool().usesRegionSelection
    }

    private fun isBeyondFace(region: BlockRegion, face: axion.common.model.RegionFace, target: BlockPos): Boolean {
        val normalized = region.normalized()
        return when (face) {
            axion.common.model.RegionFace.DOWN -> target.y < normalized.start.y
            axion.common.model.RegionFace.UP -> target.y > normalized.end.y
            axion.common.model.RegionFace.NORTH -> target.z < normalized.start.z
            axion.common.model.RegionFace.SOUTH -> target.z > normalized.end.z
            axion.common.model.RegionFace.WEST -> target.x < normalized.start.x
            axion.common.model.RegionFace.EAST -> target.x > normalized.end.x
        }
    }
}

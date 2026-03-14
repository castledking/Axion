package axion.client.tool

import axion.client.AxionClientState
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.AxionSubtool
import axion.common.model.BlockRegion
import axion.common.model.SelectionState
import axion.common.operation.SmearRegionOperation
import net.minecraft.client.MinecraftClient

object SmearToolController {
    private val dispatcher = SymmetryAwareOperationDispatcher()

    fun onEndTick(client: MinecraftClient) {
        if (!isSmearActive() && AxionClientState.smearToolState !is SmearToolState.Idle) {
            reset()
        }
    }

    fun currentPreview(): SmearPreviewState? = when (val state = AxionClientState.smearToolState) {
        SmearToolState.Idle,
        is SmearToolState.FirstCornerSet,
        is SmearToolState.RegionDefined,
            -> null

        is SmearToolState.PreviewingSmear -> state.preview
    }

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        if (!isSmearActive()) {
            return false
        }

        return when (AxionClientState.smearToolState) {
            SmearToolState.Idle,
            is SmearToolState.FirstCornerSet,
            is SmearToolState.RegionDefined,
                -> setFirstCorner()

            is SmearToolState.PreviewingSmear -> {
                reset()
                true
            }
        }
    }

    fun handleSecondaryAction(client: MinecraftClient): Boolean {
        if (!isSmearActive()) {
            return false
        }

        return when (val state = AxionClientState.smearToolState) {
            SmearToolState.Idle -> false
            is SmearToolState.FirstCornerSet -> setSecondCorner()
            is SmearToolState.RegionDefined -> setSecondCorner()
            is SmearToolState.PreviewingSmear -> confirm(state.preview)
        }
    }

    fun handleMiddleAction(client: MinecraftClient): Boolean {
        if (!isSmearActive()) {
            return false
        }

        val state = AxionClientState.smearToolState
        if (state !is SmearToolState.RegionDefined) {
            return false
        }

        val expanded = SelectionController.expandRegionToCurrentTarget(state.region) ?: return false
        val nextState = SmearToolState.RegionDefined(state.firstCorner, expanded)
        AxionClientState.updateSmearToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun handleScroll(client: MinecraftClient, scrollAmount: Double): Boolean {
        if (!isSmearActive() || scrollAmount.compareTo(0.0) == 0) {
            return false
        }

        val nextState = when (val state = AxionClientState.smearToolState) {
            SmearToolState.Idle,
            is SmearToolState.FirstCornerSet,
                -> return false

            is SmearToolState.RegionDefined -> {
                val world = client.world ?: return false
                val clipboard = ClipboardCaptureService.capture(world, state.region)
                val preview = SmearPlacementService.createInitialPreview(
                    client = client,
                    firstCorner = state.firstCorner,
                    sourceRegion = state.region,
                    clipboardBuffer = clipboard,
                    scrollAmount = scrollAmount,
                ) ?: return false
                SmearToolState.PreviewingSmear(preview)
            }

            is SmearToolState.PreviewingSmear -> {
                val preview = SmearPlacementService.nudgePreview(state.preview, scrollAmount)
                if (preview == null) {
                    SmearToolState.RegionDefined(state.preview.firstCorner, state.preview.sourceRegion)
                } else {
                    SmearToolState.PreviewingSmear(preview)
                }
            }
        }

        AxionClientState.updateSmearToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun reset() {
        val nextState = SmearToolState.Idle
        AxionClientState.updateSmearToolState(nextState)
        syncSelectionState(nextState)
    }

    private fun confirm(preview: SmearPreviewState): Boolean {
        dispatcher.dispatch(
            SmearRegionOperation(
                sourceRegion = preview.sourceRegion,
                clipboardBuffer = preview.clipboardBuffer,
                step = preview.step,
                repeatCount = preview.repeatCount,
            ),
        )
        reset()
        return true
    }

    private fun setFirstCorner(): Boolean {
        val firstCorner = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val nextState = SmearToolState.FirstCornerSet(firstCorner)
        AxionClientState.updateSmearToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    private fun setSecondCorner(): Boolean {
        val secondCorner = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val firstCorner = when (val state = AxionClientState.smearToolState) {
            SmearToolState.Idle -> return false
            is SmearToolState.FirstCornerSet -> state.firstCorner
            is SmearToolState.RegionDefined -> state.firstCorner
            is SmearToolState.PreviewingSmear -> state.preview.firstCorner
        }
        val nextState = SmearToolState.RegionDefined(
            firstCorner,
            BlockRegion(firstCorner, secondCorner).normalized(),
        )
        AxionClientState.updateSmearToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    private fun syncSelectionState(state: SmearToolState) {
        val selectionState = when (state) {
            SmearToolState.Idle -> SelectionState.Idle
            is SmearToolState.FirstCornerSet -> SelectionState.FirstCornerSet(state.firstCorner)
            is SmearToolState.RegionDefined -> SelectionState.RegionDefined(
                state.firstCorner,
                state.region.oppositeCorner(state.firstCorner),
            )
            is SmearToolState.PreviewingSmear -> SelectionState.RegionDefined(
                state.preview.firstCorner,
                state.preview.sourceRegion.oppositeCorner(state.preview.firstCorner),
            )
        }

        AxionClientState.updateSelection(selectionState)
    }

    private fun isSmearActive(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive() &&
            AxionToolSelectionController.selectedSubtool() == AxionSubtool.SMEAR
    }
}

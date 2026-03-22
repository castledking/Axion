package axion.client.tool

import axion.client.AxionClientState
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.AxionSubtool
import axion.common.model.BlockRegion
import axion.common.model.ClipboardState
import axion.common.model.SelectionState
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
        return when (state) {
            SmearToolState.Idle,
            is SmearToolState.FirstCornerSet,
                -> if (AxionClientState.middleClickMagicSelectEnabled) magicSelect(client) else false

            is SmearToolState.RegionDefined -> {
                if (AxionClientState.middleClickMagicSelectEnabled) {
                    false
                } else {
                    val expanded = SelectionController.expandRegionToCurrentTarget(client, state.region) ?: return false
                    val remappedFirstCorner = state.region.remapCorner(state.firstCorner, expanded)
                    val remappedSecondCorner = expanded.oppositeCorner(remappedFirstCorner)
                    val nextState = SmearToolState.RegionDefined(remappedFirstCorner, remappedSecondCorner, expanded, null)
                    AxionClientState.updateSmearToolState(nextState)
                    syncSelectionState(nextState)
                    true
                }
            }

            is SmearToolState.PreviewingSmear -> false
        }
    }

    fun handleScroll(client: MinecraftClient, scrollAmount: Double): Boolean {
        if (!isSmearActive() || scrollAmount.compareTo(0.0) == 0) {
            return false
        }

        val nextState = when (val state = AxionClientState.smearToolState) {
            SmearToolState.Idle,
            is SmearToolState.FirstCornerSet,
                -> {
                val magicSelection = AxionClientState.clipboardState as? ClipboardState.MagicSelection ?: return false
                val preview = SmearPlacementService.createInitialPreview(
                    client = client,
                    firstCorner = when (state) {
                        is SmearToolState.FirstCornerSet -> state.firstCorner
                        else -> magicSelection.region.start
                    },
                    sourceRegion = magicSelection.region,
                    clipboardBuffer = magicSelection.clipboardBuffer,
                    scrollAmount = scrollAmount,
                ) ?: return false
                SmearToolState.PreviewingSmear(preview)
            }

            is SmearToolState.RegionDefined -> {
                val world = client.world ?: return false
                val clipboard = state.clipboardBuffer ?: ClipboardCaptureService.capture(world, state.region)
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
                val preview = SmearPlacementService.nudgePreview(client, state.preview, scrollAmount)
                if (preview == null) {
                    SmearToolState.RegionDefined(
                        state.preview.firstCorner,
                        state.preview.sourceRegion.oppositeCorner(state.preview.firstCorner),
                        state.preview.sourceRegion,
                        state.preview.clipboardBuffer,
                    )
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
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(nextState)
    }

    private fun confirm(preview: SmearPreviewState): Boolean {
        dispatcher.dispatch(RegionRepeatPlacementService.toOperation(preview, RegionRepeatPlacementService.Mode.SMEAR))
        reset()
        return true
    }

    private fun setFirstCorner(): Boolean {
        val firstCorner = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val nextState = SmearToolState.FirstCornerSet(firstCorner)
        AxionClientState.updateSmearToolState(nextState)
        AxionClientState.updateClipboard(ClipboardState.Empty)
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
            secondCorner,
            BlockRegion(firstCorner, secondCorner).normalized(),
            null,
        )
        AxionClientState.updateSmearToolState(nextState)
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(nextState)
        return true
    }

    private fun magicSelect(
        client: MinecraftClient,
    ): Boolean {
        val world = client.world ?: return false
        val seed = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val result = MagicSelectionService.select(world, seed) ?: return false
        val merged = when (val clipboardState = AxionClientState.clipboardState) {
            is ClipboardState.MagicSelection -> MagicSelectionService.merge(
                existingRegion = clipboardState.region,
                existingClipboard = clipboardState.clipboardBuffer,
                addition = result,
            )
            ClipboardState.Empty -> result
        }
        AxionClientState.updateClipboard(
            ClipboardState.MagicSelection(
                region = merged.region,
                clipboardBuffer = merged.clipboardBuffer,
            ),
        )
        return true
    }

    private fun syncSelectionState(state: SmearToolState) {
        val selectionState = when (state) {
            SmearToolState.Idle -> SelectionState.Idle
            is SmearToolState.FirstCornerSet -> SelectionState.FirstCornerSet(state.firstCorner)
            is SmearToolState.RegionDefined -> SelectionState.RegionDefined(
                state.firstCorner,
                state.secondCorner,
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

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
import axion.client.tool.ClipboardTransformService
import axion.client.tool.PlacementMirrorAxis

object StackToolController {
    private val dispatcher = SymmetryAwareOperationDispatcher()

    fun onEndTick(client: MinecraftClient) {
        if (!isStackActive() && AxionClientState.stackToolState !is StackToolState.Idle) {
            reset()
        }
    }

    fun currentPreview(): StackPreviewState? = when (val state = AxionClientState.stackToolState) {
        StackToolState.Idle,
        is StackToolState.FirstCornerSet,
        is StackToolState.RegionDefined,
            -> null

        is StackToolState.PreviewingStack -> state.preview
    }

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        if (!isStackActive()) {
            return false
        }

        return when (AxionClientState.stackToolState) {
            StackToolState.Idle,
            is StackToolState.FirstCornerSet,
            is StackToolState.RegionDefined,
                -> setFirstCorner()

            is StackToolState.PreviewingStack -> {
                reset()
                true
            }
        }
    }

    fun handleSecondaryAction(client: MinecraftClient): Boolean {
        if (!isStackActive()) {
            return false
        }

        return when (val state = AxionClientState.stackToolState) {
            StackToolState.Idle -> false
            is StackToolState.FirstCornerSet -> setSecondCorner()
            is StackToolState.RegionDefined -> setSecondCorner()
            is StackToolState.PreviewingStack -> confirm(state.preview)
        }
    }

    fun handleMiddleAction(client: MinecraftClient): Boolean {
        if (!isStackActive()) {
            return false
        }

        val state = AxionClientState.stackToolState
        return when (state) {
            StackToolState.Idle,
            is StackToolState.FirstCornerSet,
                -> if (AxionClientState.middleClickMagicSelectEnabled) magicSelect(client) else false

            is StackToolState.RegionDefined -> {
                if (AxionClientState.middleClickMagicSelectEnabled) {
                    false
                } else {
                    val expanded = SelectionController.expandRegionToCurrentTarget(client, state.region) ?: return false
                    val remappedFirstCorner = state.region.remapCorner(state.firstCorner, expanded)
                    val remappedSecondCorner = expanded.oppositeCorner(remappedFirstCorner)
                    val nextState = StackToolState.RegionDefined(remappedFirstCorner, remappedSecondCorner, expanded, null)
                    AxionClientState.updateStackToolState(nextState)
                    syncSelectionState(nextState)
                    true
                }
            }

            is StackToolState.PreviewingStack -> false
        }
    }

    fun handleScroll(client: MinecraftClient, scrollAmount: Double): Boolean {
        if (!isStackActive() || scrollAmount.compareTo(0.0) == 0) {
            return false
        }

        val nextState = when (val state = AxionClientState.stackToolState) {
            StackToolState.Idle,
            is StackToolState.FirstCornerSet,
                -> {
                val magicSelection = AxionClientState.clipboardState as? ClipboardState.MagicSelection ?: return false
                val preview = StackPlacementService.createInitialPreview(
                    client = client,
                    firstCorner = when (state) {
                        is StackToolState.FirstCornerSet -> state.firstCorner
                        else -> magicSelection.region.start
                    },
                    sourceRegion = magicSelection.region,
                    clipboardBuffer = magicSelection.clipboardBuffer,
                    scrollAmount = scrollAmount,
                ) ?: return false
                StackToolState.PreviewingStack(preview)
            }

            is StackToolState.RegionDefined -> {
                val world = client.world ?: return false
                val clipboard = state.clipboardBuffer ?: ClipboardCaptureService.capture(world, state.region)
                val preview = StackPlacementService.createInitialPreview(
                    client = client,
                    firstCorner = state.firstCorner,
                    sourceRegion = state.region,
                    clipboardBuffer = clipboard,
                    scrollAmount = scrollAmount,
                ) ?: return false
                StackToolState.PreviewingStack(preview)
            }

            is StackToolState.PreviewingStack -> {
                val preview = StackPlacementService.nudgePreview(client, state.preview, scrollAmount)
                if (preview == null) {
                    StackToolState.RegionDefined(
                        state.preview.firstCorner,
                        state.preview.sourceRegion.oppositeCorner(state.preview.firstCorner),
                        state.preview.sourceRegion,
                        state.preview.clipboardBuffer,
                    )
                } else {
                    StackToolState.PreviewingStack(preview)
                }
            }
        }

        AxionClientState.updateStackToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun handleRotateAction(): Boolean {
        val state = AxionClientState.stackToolState
        if (state !is StackToolState.PreviewingStack) return false
        val preview = state.preview
        val newTransform = preview.transform.rotateClockwise()
        val transformed = ClipboardTransformService.transform(preview.sourceClipboardBuffer, newTransform)
        val nextState = StackToolState.PreviewingStack(
            preview.copy(
                clipboardBuffer = transformed,
                transform = newTransform,
            ),
        )
        AxionClientState.updateStackToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun handleMirrorAction(client: MinecraftClient): Boolean {
        val state = AxionClientState.stackToolState
        if (state !is StackToolState.PreviewingStack) return false
        val preview = state.preview
        val axis = dominantMirrorAxis(client)
        val newTransform = preview.transform.toggleMirror(axis)
        val transformed = ClipboardTransformService.transform(preview.sourceClipboardBuffer, newTransform)
        val nextState = StackToolState.PreviewingStack(
            preview.copy(
                clipboardBuffer = transformed,
                transform = newTransform,
            ),
        )
        AxionClientState.updateStackToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    private fun dominantMirrorAxis(client: MinecraftClient): PlacementMirrorAxis {
        val look = client.player?.rotationVecClient ?: return PlacementMirrorAxis.X
        val ax = kotlin.math.abs(look.x)
        val ay = kotlin.math.abs(look.y)
        val az = kotlin.math.abs(look.z)
        return when {
            ay >= ax && ay >= az -> PlacementMirrorAxis.Y
            ax >= az -> PlacementMirrorAxis.X
            else -> PlacementMirrorAxis.Z
        }
    }

    fun reset() {
        val nextState = StackToolState.Idle
        AxionClientState.updateStackToolState(nextState)
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(nextState)
    }

    private fun confirm(preview: StackPreviewState): Boolean {
        dispatcher.dispatch(StackPlacementService.toOperation(preview))
        reset()
        return true
    }

    private fun setFirstCorner(): Boolean {
        val firstCorner = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val nextState = StackToolState.FirstCornerSet(firstCorner)
        AxionClientState.updateStackToolState(nextState)
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(nextState)
        return true
    }

    private fun setSecondCorner(): Boolean {
        val secondCorner = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val firstCorner = when (val state = AxionClientState.stackToolState) {
            StackToolState.Idle -> return false
            is StackToolState.FirstCornerSet -> state.firstCorner
            is StackToolState.RegionDefined -> state.firstCorner
            is StackToolState.PreviewingStack -> state.preview.firstCorner
        }
        val nextState = StackToolState.RegionDefined(
            firstCorner,
            secondCorner,
            BlockRegion(firstCorner, secondCorner).normalized(),
            null,
        )
        AxionClientState.updateStackToolState(nextState)
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

    private fun syncSelectionState(state: StackToolState) {
        val selectionState = when (state) {
            StackToolState.Idle -> SelectionState.Idle
            is StackToolState.FirstCornerSet -> SelectionState.FirstCornerSet(state.firstCorner)
            is StackToolState.RegionDefined -> SelectionState.RegionDefined(
                state.firstCorner,
                state.secondCorner,
            )
            is StackToolState.PreviewingStack -> SelectionState.RegionDefined(
                state.preview.firstCorner,
                state.preview.sourceRegion.oppositeCorner(state.preview.firstCorner),
            )
        }

        AxionClientState.updateSelection(selectionState)
    }

    private fun isStackActive(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive() &&
            AxionToolSelectionController.selectedSubtool() == AxionSubtool.STACK
    }
}

package axion.client.tool

import axion.client.AxionClientState
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.BlockRegion
import axion.common.model.ClipboardState
import axion.common.model.SelectionState
import axion.protocol.AxionTransportCodec
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

object PlacementToolController {
    private val dispatcher = SymmetryAwareOperationDispatcher()

    fun onEndTick(client: MinecraftClient) {
        val state = AxionClientState.placementToolState
        if (!isPlacementActive()) {
            if (state !is CloneToolState.Idle) {
                reset()
            }
            return
        }

        val activeMode = activeMode()
        if (state !is CloneToolState.Idle && state.modeOrNull() != activeMode) {
            reset()
            return
        }

        if (state is CloneToolState.PreviewingOffset) {
            AxionClientState.updatePlacementToolState(CloneToolState.AwaitingConfirm(state.preview))
        }
    }

    fun currentPreview(): ClonePreviewState? = when (val state = AxionClientState.placementToolState) {
        CloneToolState.Idle,
        is CloneToolState.FirstCornerSet,
        is CloneToolState.RegionDefined,
            -> null

        is CloneToolState.PreviewingOffset -> state.preview
        is CloneToolState.AwaitingConfirm -> state.preview
    }

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        if (!isPlacementActive()) {
            return false
        }

        return when (val state = AxionClientState.placementToolState) {
            CloneToolState.Idle -> setFirstCorner()
            is CloneToolState.FirstCornerSet -> setFirstCorner()
            is CloneToolState.RegionDefined -> setFirstCorner()
            is CloneToolState.PreviewingOffset,
            is CloneToolState.AwaitingConfirm,
                -> {
                    reset()
                    true
                }
        }
    }

    fun handleSecondaryAction(client: MinecraftClient): Boolean {
        if (!isPlacementActive()) {
            return false
        }

        return when (val state = AxionClientState.placementToolState) {
            CloneToolState.Idle -> false
            is CloneToolState.FirstCornerSet -> setSecondCorner(state.firstCorner)
            is CloneToolState.RegionDefined -> setSecondCorner(state.firstCorner)
            is CloneToolState.PreviewingOffset -> confirm(state.preview)
            is CloneToolState.AwaitingConfirm -> confirm(state.preview)
        }
    }

    fun handleMiddleAction(client: MinecraftClient): Boolean {
        if (!isPlacementActive()) {
            return false
        }

        return when (val state = AxionClientState.placementToolState) {
            CloneToolState.Idle,
            is CloneToolState.FirstCornerSet,
                -> if (AxionClientState.middleClickMagicSelectEnabled) magicSelect(client) else false

            is CloneToolState.RegionDefined -> {
                if (AxionClientState.middleClickMagicSelectEnabled) {
                    false
                } else {
                    expandSelectionFace(client, state.region)
                }
            }
            is CloneToolState.PreviewingOffset -> reanchorPreview(state.preview)
            is CloneToolState.AwaitingConfirm -> reanchorPreview(state.preview)
        }
    }

    fun handleScroll(client: MinecraftClient, scrollAmount: Double): Boolean {
        val mode = activeMode() ?: return false
        if (scrollAmount.compareTo(0.0) == 0) {
            return false
        }

        val nextState = when (val state = AxionClientState.placementToolState) {
            CloneToolState.Idle,
            is CloneToolState.FirstCornerSet,
                -> {
                val magicSelection = AxionClientState.clipboardState as? ClipboardState.MagicSelection ?: return false
                CloneToolState.PreviewingOffset(
                    ClonePlacementService.initialPreview(
                        client = client,
                        mode = mode,
                        firstCorner = when (state) {
                            is CloneToolState.FirstCornerSet -> state.firstCorner
                            else -> magicSelection.region.start
                        },
                        sourceRegion = magicSelection.region,
                        clipboardBuffer = magicSelection.clipboardBuffer,
                        scrollAmount = scrollAmount,
                    ),
                )
            }

            is CloneToolState.RegionDefined -> {
                val world = client.world ?: return false
                val clipboard = state.clipboardBuffer ?: ClipboardCaptureService.capture(world, state.region)
                CloneToolState.PreviewingOffset(
                    ClonePlacementService.initialPreview(
                        client = client,
                        mode = mode,
                        firstCorner = state.firstCorner,
                        sourceRegion = state.region,
                        clipboardBuffer = clipboard,
                        scrollAmount = scrollAmount,
                    ),
                )
            }

            is CloneToolState.PreviewingOffset -> {
                CloneToolState.PreviewingOffset(ClonePlacementService.nudgePreview(client, state.preview, scrollAmount))
            }

            is CloneToolState.AwaitingConfirm -> {
                CloneToolState.PreviewingOffset(ClonePlacementService.nudgePreview(client, state.preview, scrollAmount))
            }
        }

        AxionClientState.updatePlacementToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun handleRotateAction(): Boolean {
        val nextState = when (val state = AxionClientState.placementToolState) {
            is CloneToolState.PreviewingOffset -> CloneToolState.AwaitingConfirm(
                ClonePlacementService.rotatePreview(state.preview),
            )

            is CloneToolState.AwaitingConfirm -> CloneToolState.AwaitingConfirm(
                ClonePlacementService.rotatePreview(state.preview),
            )

            else -> return false
        }

        AxionClientState.updatePlacementToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun handleMirrorAction(client: MinecraftClient): Boolean {
        val nextState = when (val state = AxionClientState.placementToolState) {
            is CloneToolState.PreviewingOffset -> CloneToolState.AwaitingConfirm(
                ClonePlacementService.mirrorPreview(state.preview, client),
            )

            is CloneToolState.AwaitingConfirm -> CloneToolState.AwaitingConfirm(
                ClonePlacementService.mirrorPreview(state.preview, client),
            )

            else -> return false
        }

        AxionClientState.updatePlacementToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    fun reset() {
        val state = CloneToolState.Idle
        AxionClientState.updatePlacementToolState(state)
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(state)
    }

    private fun confirm(preview: ClonePreviewState): Boolean {
        val operation = PlacementCommitService.toOperation(preview)

        // Validate operation size before dispatching to prevent crashes
        val estimatedSize = estimateOperationSize(operation)
        if (estimatedSize > AxionTransportCodec.MAX_SERIALIZED_BYTES) {
            val player = MinecraftClient.getInstance().player
            player?.sendMessage(
                Text.literal("Selection too large! Please select a smaller region (max ~${AxionTransportCodec.MAX_SERIALIZED_BYTES / 1024 / 1024}MB) or increase the limit."),
                false
            )
            return false
        }

        dispatcher.dispatch(operation)
        if (preview.mode == PlacementToolMode.CLONE) {
            val state = CloneToolState.AwaitingConfirm(preview)
            AxionClientState.updatePlacementToolState(state)
            syncSelectionState(state)
        } else {
            reset()
        }
        return true
    }

    private fun estimateOperationSize(operation: axion.common.operation.EditOperation): Int {
        // Quick estimation by counting placements (each placement is roughly 100+ bytes serialized)
        return when (operation) {
            is axion.common.operation.SymmetryPlacementOperation -> operation.placements.size * 150
            is axion.common.operation.CompositeOperation -> {
                operation.operations.sumOf { op ->
                    when (op) {
                        is axion.common.operation.SymmetryPlacementOperation -> op.placements.size * 150
                        else -> 100 // estimate for other operation types
                    }
                }
            }
            else -> 1000 // conservative estimate for other operations
        }
    }

    private fun expandSelectionFace(client: MinecraftClient, region: BlockRegion): Boolean {
        val expandedRegion = SelectionController.expandRegionToCurrentTarget(client, region) ?: return false
        val mode = AxionClientState.placementToolState.modeOrNull() ?: activeMode() ?: return false
        val firstCorner = AxionClientState.placementToolState.firstCornerOrNull()
            ?.let { region.remapCorner(it, expandedRegion) }
            ?: expandedRegion.start
        val secondCorner = expandedRegion.oppositeCorner(firstCorner)
        val nextState = CloneToolState.RegionDefined(mode, firstCorner, secondCorner, expandedRegion, null)
        AxionClientState.updatePlacementToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    private fun reanchorPreview(preview: ClonePreviewState): Boolean {
        val anchorPos = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val nextState = CloneToolState.AwaitingConfirm(
            ClonePlacementService.reanchorPreview(preview, anchorPos),
        )
        AxionClientState.updatePlacementToolState(nextState)
        syncSelectionState(nextState)
        return true
    }

    private fun setFirstCorner(): Boolean {
        val mode = activeMode() ?: return false
        val blockPos = SelectionController.currentTarget().blockPosOrNull() ?: return false
        val nextState = CloneToolState.FirstCornerSet(mode, blockPos.toImmutable())
        AxionClientState.updatePlacementToolState(nextState)
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(nextState)
        return true
    }

    private fun setSecondCorner(firstCornerFallback: BlockPos): Boolean {
        val secondCorner = SelectionController.currentTarget().blockPosOrNull() ?: return false
        val currentState = AxionClientState.placementToolState
        val currentFirstCorner = when (currentState) {
            is CloneToolState.FirstCornerSet -> currentState.firstCorner
            is CloneToolState.RegionDefined -> currentState.firstCorner
            else -> firstCornerFallback
        }
        val mode = currentState.modeOrNull() ?: activeMode() ?: return false

        val nextState = CloneToolState.RegionDefined(
            mode,
            currentFirstCorner.toImmutable(),
            secondCorner.toImmutable(),
            BlockRegion(currentFirstCorner.toImmutable(), secondCorner.toImmutable()).normalized(),
            null,
        )
        AxionClientState.updatePlacementToolState(nextState)
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(nextState)
        return true
    }

    private fun magicSelect(
        client: MinecraftClient,
    ): Boolean {
        activeMode() ?: return false
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

    private fun syncSelectionState(state: CloneToolState) {
        val selectionState = when (state) {
            CloneToolState.Idle -> SelectionState.Idle
            is CloneToolState.FirstCornerSet -> SelectionState.FirstCornerSet(state.firstCorner)
            is CloneToolState.RegionDefined -> SelectionState.RegionDefined(
                state.firstCorner,
                state.secondCorner,
            )
            is CloneToolState.PreviewingOffset -> SelectionState.Idle
            is CloneToolState.AwaitingConfirm -> SelectionState.Idle
        }

        AxionClientState.updateSelection(selectionState)
    }

    private fun isPlacementActive(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive() && activeMode() != null
    }

    private fun activeMode(): PlacementToolMode? {
        return PlacementToolMode.fromSubtool(AxionToolSelectionController.selectedSubtool())
    }
}

private fun CloneToolState.modeOrNull(): PlacementToolMode? = when (this) {
    CloneToolState.Idle -> null
    is CloneToolState.FirstCornerSet -> mode
    is CloneToolState.RegionDefined -> mode
    is CloneToolState.PreviewingOffset -> preview.mode
    is CloneToolState.AwaitingConfirm -> preview.mode
}

private fun CloneToolState.firstCornerOrNull(): BlockPos? = when (this) {
    CloneToolState.Idle -> null
    is CloneToolState.FirstCornerSet -> firstCorner
    is CloneToolState.RegionDefined -> firstCorner
    is CloneToolState.PreviewingOffset -> preview.firstCorner
    is CloneToolState.AwaitingConfirm -> preview.firstCorner
}

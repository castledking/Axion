package axion.client.tool

import axion.client.AxionClientState
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.AxionSubtool
import axion.common.model.BlockRegion
import axion.common.model.ClipboardState
import axion.common.model.SelectionState
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.DeleteEntitiesOperation
import axion.common.operation.SymmetryBlockPlacement
import axion.common.operation.SymmetryPlacementOperation
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos

object EraseToolController {
    private val dispatcher = SymmetryAwareOperationDispatcher()

    fun onEndTick(client: MinecraftClient) {
        if (!isEraseActive() && AxionClientState.eraseToolState !is EraseToolState.Idle) {
            reset()
        }
    }

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        if (!isEraseActive()) {
            return false
        }

        val blockPos = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val nextState = EraseToolState.FirstCornerSet(blockPos)
        AxionClientState.updateEraseToolState(nextState)
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(nextState)
        return true
    }

    fun handleSecondaryAction(client: MinecraftClient): Boolean {
        if (!isEraseActive()) {
            return false
        }

        val secondCorner = SelectionController.currentTarget().blockPosOrNull()?.toImmutable() ?: return false
        val firstCorner = when (val state = AxionClientState.eraseToolState) {
            EraseToolState.Idle -> return false
            is EraseToolState.FirstCornerSet -> state.firstCorner
            is EraseToolState.RegionDefined -> state.firstCorner
        }

        val nextState = EraseToolState.RegionDefined(
            firstCorner,
            secondCorner,
            BlockRegion(firstCorner, secondCorner).normalized(),
            null,
        )
        AxionClientState.updateEraseToolState(nextState)
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(nextState)
        return true
    }

    fun handleMiddleAction(client: MinecraftClient): Boolean {
        if (!isEraseActive()) {
            return false
        }

        return when (val state = AxionClientState.eraseToolState) {
            EraseToolState.Idle,
            is EraseToolState.FirstCornerSet,
                -> if (AxionClientState.middleClickMagicSelectEnabled) magicSelect(client) else false

            is EraseToolState.RegionDefined -> {
                if (AxionClientState.middleClickMagicSelectEnabled) {
                    false
                } else {
                    val expanded = SelectionController.expandRegionToCurrentTarget(client, state.region) ?: return false
                    val remappedFirstCorner = state.region.remapCorner(state.firstCorner, expanded)
                    val remappedSecondCorner = expanded.oppositeCorner(remappedFirstCorner)
                    val nextState = EraseToolState.RegionDefined(remappedFirstCorner, remappedSecondCorner, expanded, null)
                    AxionClientState.updateEraseToolState(nextState)
                    syncSelectionState(nextState)
                    true
                }
            }
        }
    }

    fun handleDeleteAction(client: MinecraftClient): Boolean {
        if (!isEraseActive()) {
            return false
        }

        val operation = when (val state = AxionClientState.eraseToolState) {
            is EraseToolState.RegionDefined -> state.clipboardBuffer?.let { clipboard ->
                eraseOperation(
                    state.region,
                    SymmetryPlacementOperation(
                        clipboard.cells.map { cell ->
                            SymmetryBlockPlacement(
                                pos = state.region.minCorner().add(cell.offset),
                                state = Blocks.AIR.defaultState,
                                blockEntityData = null,
                            )
                        },
                    ),
                )
            } ?: eraseOperation(state.region)
            EraseToolState.Idle,
            is EraseToolState.FirstCornerSet,
                -> {
                val magic = AxionClientState.clipboardState as? ClipboardState.MagicSelection ?: return false
                eraseOperation(
                    magic.region,
                    SymmetryPlacementOperation(
                        magic.clipboardBuffer.cells.map { cell ->
                            SymmetryBlockPlacement(
                                pos = magic.region.minCorner().add(cell.offset),
                                state = Blocks.AIR.defaultState,
                                blockEntityData = null,
                            )
                        },
                    ),
                )
            }
        }
        dispatcher.dispatch(operation)
        reset()
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

    fun reset() {
        val nextState = EraseToolState.Idle
        AxionClientState.updateEraseToolState(nextState)
        AxionClientState.updateClipboard(ClipboardState.Empty)
        syncSelectionState(nextState)
    }

    private fun syncSelectionState(state: EraseToolState) {
        val selectionState = when (state) {
            EraseToolState.Idle -> SelectionState.Idle
            is EraseToolState.FirstCornerSet -> SelectionState.FirstCornerSet(state.firstCorner)
            is EraseToolState.RegionDefined -> SelectionState.RegionDefined(
                state.firstCorner,
                state.secondCorner,
            )
        }

        AxionClientState.updateSelection(selectionState)
    }

    private fun isEraseActive(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive() &&
            AxionToolSelectionController.selectedSubtool() == AxionSubtool.ERASE
    }

    private fun eraseOperation(
        region: BlockRegion,
        blockOperation: axion.common.operation.EditOperation = ClearRegionOperation(region),
    ): axion.common.operation.EditOperation {
        if (!AxionClientState.copyEntitiesEnabled) {
            return blockOperation
        }

        return CompositeOperation(
            listOf(
                blockOperation,
                DeleteEntitiesOperation(region),
            ),
        )
    }
}

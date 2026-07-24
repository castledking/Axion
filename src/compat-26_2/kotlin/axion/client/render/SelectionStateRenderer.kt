package axion.client.render

import axion.client.AxionClientState
import axion.client.selection.SelectionBounds
import axion.client.tool.PlacementToolController
import axion.client.tool.StackToolController
import axion.client.tool.AxionToolSelectionController
import axion.client.tool.CloneToolState
import axion.client.tool.EraseToolState
import axion.client.tool.SmearToolState
import axion.client.tool.StackToolState
import axion.common.model.ClipboardBuffer
import axion.common.model.AxionSubtool
import axion.common.model.SelectionState

object SelectionStateRenderer {
    private const val REGION_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val LINE_WIDTH: Float = 2.0f
    private const val SELECTION_BASE_FILL_COLOR: Int = 0xFFCC5656.toInt()
    private const val SELECTION_BASE_FILL_ALPHA: Int = 1
    private const val SELECTION_PULSE_FILL_COLOR: Int = 0xFF7C98FF.toInt()
    private const val SELECTION_PULSE_MIN_ALPHA: Int = 0
    // Peak alpha of the phased fill. The pulse now passes through fully
    // transparent at the crossover, so the peaks can be far stronger than the
    // old always-on stack without the selection ever going muddy.
    private const val SELECTION_PULSE_MAX_ALPHA: Int = 26
    // The box fill is looked through end to end rather than hugging a surface,
    // so it needs to stay far weaker to avoid masking the preview behind it.
    private const val SELECTION_VOLUME_PULSE_MAX_ALPHA: Int = 12

    fun render(context: AxionWorldRenderContext) {
        if (!shouldRenderSelectionPulse()) {
            return
        }

        if (hasActivePreview()) {
            return
        }

        val state = AxionClientState.selectionState

        when (state) {
            SelectionState.Idle -> {
                val pendingMagicSelection = AxionClientState.clipboardState as? axion.common.model.ClipboardState.MagicSelection ?: return
                BlockPreviewPipeline.renderSelection(
                    context = context,
                    scene = BlockPreviewPipeline.SelectionScene(
                        origins = listOf(pendingMagicSelection.region.minCorner()),
                        selectionClipboard = pendingMagicSelection.clipboardBuffer,
                        sparse = true,
                        outlineColor = REGION_COLOR,
                        lineWidth = LINE_WIDTH,
                        baseFillColor = SELECTION_BASE_FILL_COLOR,
                        baseAlpha = 0,
                        pulseFillColor = null,
                        pulseMinAlpha = 0,
                        pulseMaxAlpha = 0,
                    ),
                )
            }

            is SelectionState.FirstCornerSet -> {
                BlockPreviewPipeline.renderSelection(
                    context = context,
                    scene = BlockPreviewPipeline.SelectionScene(
                        origins = emptyList(),
                        selectionClipboard = null,
                        sparse = false,
                        outlineColor = REGION_COLOR,
                        lineWidth = LINE_WIDTH,
                        aggregateBox = SelectionBounds.blockBox(state.firstCorner),
                        baseFillColor = SELECTION_BASE_FILL_COLOR,
                        baseAlpha = SELECTION_BASE_FILL_ALPHA,
                        pulseFillColor = SELECTION_PULSE_FILL_COLOR,
                        pulseMinAlpha = SELECTION_PULSE_MIN_ALPHA,
                        pulseMaxAlpha = SELECTION_PULSE_MAX_ALPHA,
                    ),
                )
            }

            is SelectionState.RegionDefined -> {
                val region = state.region()
                // Tint the blocks in the region rather than flooding the whole
                // cuboid: a solid volume fill sits in front of the GPU preview and
                // hides it. Every tool keeps the region's clipboard on its own
                // state, so fall back to the volume fill only when none is ready.
                val contour = regionContourClipboard()
                BlockPreviewPipeline.renderSelection(
                    context = context,
                    scene = BlockPreviewPipeline.SelectionScene(
                        origins = if (contour != null) listOf(region.minCorner()) else emptyList(),
                        selectionClipboard = contour,
                        sparse = contour != null,
                        outlineColor = REGION_COLOR,
                        lineWidth = LINE_WIDTH,
                        aggregateBox = SelectionBounds.regionBox(region),
                        baseFillColor = SELECTION_BASE_FILL_COLOR,
                        baseAlpha = SELECTION_BASE_FILL_ALPHA,
                        pulseFillColor = SELECTION_PULSE_FILL_COLOR,
                        pulseMinAlpha = SELECTION_PULSE_MIN_ALPHA,
                        pulseMaxAlpha = if (contour != null) {
                            SELECTION_PULSE_MAX_ALPHA
                        } else {
                            SELECTION_VOLUME_PULSE_MAX_ALPHA
                        },
                    ),
                )
            }
        }
    }

    /**
     * The region clipboard for whichever tool owns the current selection.
     *
     * Present once a region has been captured, which is what lets the selection
     * fill follow the block contours instead of drawing a solid cuboid.
     */
    private fun regionContourClipboard(): ClipboardBuffer? {
        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.CLONE,
            AxionSubtool.MOVE,
                -> (AxionClientState.placementToolState as? CloneToolState.RegionDefined)?.clipboardBuffer

            AxionSubtool.STACK ->
                (AxionClientState.stackToolState as? StackToolState.RegionDefined)?.clipboardBuffer

            AxionSubtool.SMEAR ->
                (AxionClientState.smearToolState as? SmearToolState.RegionDefined)?.clipboardBuffer

            AxionSubtool.ERASE ->
                (AxionClientState.eraseToolState as? EraseToolState.RegionDefined)?.clipboardBuffer

            else -> null
        }
    }

    private fun shouldRenderSelectionPulse(): Boolean {
        if (!AxionToolSelectionController.isAxionSelected()) {
            return false
        }

        return when (AxionClientState.selectedSubtool) {
            AxionSubtool.MOVE,
            AxionSubtool.CLONE,
            AxionSubtool.STACK,
            AxionSubtool.SMEAR,
            AxionSubtool.ERASE,
                -> true

            AxionSubtool.SETUP_SYMMETRY,
            AxionSubtool.EXTRUDE,
                -> false
        }
    }

    private fun hasActivePreview(): Boolean {
        return PlacementToolController.currentPreview() != null ||
            StackToolController.currentPreview() != null
    }
}

package axion.client.render

import axion.client.AxionClientState
import axion.client.selection.SelectionBounds
import axion.client.tool.PlacementToolController
import axion.client.tool.SmearToolController
import axion.client.tool.StackToolController
import axion.client.tool.AxionToolSelectionController
import axion.common.model.AxionSubtool
import axion.common.model.SelectionState

object SelectionBoxRenderer {
    private const val REGION_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val LINE_WIDTH: Float = 2.0f
    private const val SELECTION_BASE_FILL_COLOR: Int = 0xFFCC5656.toInt()
    private const val SELECTION_BASE_FILL_ALPHA: Int = 16
    private const val SELECTION_PULSE_FILL_COLOR: Int = 0xFF7C98FF.toInt()
    private const val SELECTION_PULSE_MIN_ALPHA: Int = 4
    private const val SELECTION_PULSE_MAX_ALPHA: Int = 34

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
                        baseAlpha = SELECTION_BASE_FILL_ALPHA,
                        pulseFillColor = SELECTION_PULSE_FILL_COLOR,
                        pulseMinAlpha = SELECTION_PULSE_MIN_ALPHA,
                        pulseMaxAlpha = SELECTION_PULSE_MAX_ALPHA,
                    ),
                )
                return
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
                BlockPreviewPipeline.renderSelection(
                    context = context,
                    scene = BlockPreviewPipeline.SelectionScene(
                        origins = emptyList(),
                        selectionClipboard = null,
                        sparse = false,
                        outlineColor = REGION_COLOR,
                        lineWidth = LINE_WIDTH,
                        aggregateBox = SelectionBounds.regionBox(state.region()),
                        baseFillColor = SELECTION_BASE_FILL_COLOR,
                        baseAlpha = SELECTION_BASE_FILL_ALPHA,
                        pulseFillColor = SELECTION_PULSE_FILL_COLOR,
                        pulseMinAlpha = SELECTION_PULSE_MIN_ALPHA,
                        pulseMaxAlpha = SELECTION_PULSE_MAX_ALPHA,
                    ),
                )
            }
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
            StackToolController.currentPreview() != null ||
            SmearToolController.currentPreview() != null
    }
}

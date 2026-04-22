package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.tool.PlacementToolController
import axion.client.tool.PlacementToolMode
import axion.common.model.ClipboardBuffer
import net.minecraft.block.Blocks

object PlacementPreviewRenderer {
    private const val DESTINATION_GHOST_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val MOVE_SOURCE_COLOR: Int = 0xFF9EA6B6.toInt()
    private const val CLONE_DESTINATION_COLOR: Int = 0xFFFFB347.toInt()
    private const val MOVE_DESTINATION_COLOR: Int = 0xFF7EE6A6.toInt()
    private const val LINE_WIDTH: Float = 1.75f
    private const val DEFAULT_GHOST_ALPHA: Int = 180
    private const val MOVE_SOURCE_GHOST_ALPHA: Int = 220
    private const val MOVE_DESTINATION_GHOST_ALPHA: Int = 210
    private const val SPARSE_DESTINATION_GHOST_ALPHA: Int = 235
    private const val MOVE_SOURCE_GHOST_SCALE: Float = 1.01f
    private const val DESTINATION_GHOST_SCALE: Float = 0.985f
    private const val MAX_MOVE_DETAILED_SELECTION_CELLS: Int = 4096
    private const val MAX_MOVE_SOURCE_CELLS: Int = 4096
    private const val MAX_MOVE_DESTINATION_GHOST_CELLS: Int = 8192
    private const val MAX_CLONE_DESTINATION_GHOST_CELLS: Int = 8192
    private val moveSourceClipboardCache = java.util.WeakHashMap<ClipboardBuffer, ClipboardBuffer?>()

    fun render(context: AxionWorldRenderContext) {
        val preview = PlacementToolController.currentPreview() ?: return
        val destinationColor = when (preview.mode) {
            PlacementToolMode.CLONE -> CLONE_DESTINATION_COLOR
            PlacementToolMode.MOVE -> MOVE_DESTINATION_COLOR
        }
        val destinationSelectionClipboard = ClipboardSelectionRenderer.sparseClipboard(preview.destinationClipboardBuffer)
        val destinationGhostClipboard = when (preview.mode) {
            PlacementToolMode.MOVE -> ClipboardSelectionRenderer.surfaceClipboard(destinationSelectionClipboard)
            PlacementToolMode.CLONE -> if (ClipboardSelectionRenderer.isSparse(preview.destinationRegion, destinationSelectionClipboard)) {
                ClipboardSelectionRenderer.surfaceClipboard(destinationSelectionClipboard)
            } else {
                preview.destinationClipboardBuffer
            }
        }
        val sparseDestination = ClipboardSelectionRenderer.isSparse(
            preview.destinationRegion,
            destinationSelectionClipboard,
        )
        val detailedMovePreview = preview.mode != PlacementToolMode.MOVE ||
            destinationSelectionClipboard.nonAirCells().size <= MAX_MOVE_DETAILED_SELECTION_CELLS
        if (preview.mode == PlacementToolMode.MOVE) {
            val sourceClipboard = if (detailedMovePreview) moveSourceClipboard(preview.sourceClipboardBuffer) else null
            if (sourceClipboard != null) {
                BlockPreviewPipeline.renderOverlay(
                    context = context,
                    scene = BlockPreviewPipeline.OverlayScene(
                        origins = listOf(preview.sourceRegion.minCorner()),
                        clipboard = sourceClipboard,
                        color = MOVE_SOURCE_COLOR,
                        alpha = MOVE_SOURCE_GHOST_ALPHA,
                        scale = MOVE_SOURCE_GHOST_SCALE,
                    ),
                )
            }
        }
        val renderDestinationGhost = detailedMovePreview && destinationGhostClipboard.nonAirCells().size <= when (preview.mode) {
            PlacementToolMode.MOVE -> MAX_MOVE_DESTINATION_GHOST_CELLS
            PlacementToolMode.CLONE -> MAX_CLONE_DESTINATION_GHOST_CELLS
        }

        if (preview.mode == PlacementToolMode.MOVE && !detailedMovePreview) {
            BlockPreviewPipeline.renderDestination(
                context = context,
                scene = BlockPreviewPipeline.Scene(
                    origins = listOf(preview.destinationRegion.minCorner()),
                    selectionClipboard = destinationSelectionClipboard,
                    shellClipboard = preview.destinationClipboardBuffer,
                    fallbackGhostClipboard = destinationGhostClipboard,
                    sparse = false,
                    outlineColor = destinationColor,
                    lineWidth = LINE_WIDTH,
                    ghostColor = DESTINATION_GHOST_COLOR,
                    ghostAlpha = when {
                        sparseDestination -> SPARSE_DESTINATION_GHOST_ALPHA
                        preview.mode == PlacementToolMode.MOVE -> MOVE_DESTINATION_GHOST_ALPHA
                        else -> DEFAULT_GHOST_ALPHA
                    },
                    ghostScale = DESTINATION_GHOST_SCALE,
                    aggregateBox = SelectionBounds.regionBox(preview.destinationRegion),
                    renderGhost = false,
                ),
            )
        } else {
            BlockPreviewPipeline.renderDestination(
                context = context,
                scene = BlockPreviewPipeline.Scene(
                    origins = listOf(preview.destinationRegion.minCorner()),
                    selectionClipboard = destinationSelectionClipboard,
                    shellClipboard = preview.destinationClipboardBuffer,
                    fallbackGhostClipboard = destinationGhostClipboard,
                    sparse = sparseDestination,
                    outlineColor = destinationColor,
                    lineWidth = LINE_WIDTH,
                    ghostColor = DESTINATION_GHOST_COLOR,
                    ghostAlpha = when {
                        sparseDestination -> SPARSE_DESTINATION_GHOST_ALPHA
                        preview.mode == PlacementToolMode.MOVE -> MOVE_DESTINATION_GHOST_ALPHA
                        else -> DEFAULT_GHOST_ALPHA
                    },
                    ghostScale = DESTINATION_GHOST_SCALE,
                    aggregateBox = SelectionBounds.regionBox(preview.destinationRegion),
                    renderGhost = renderDestinationGhost,
                ),
            )
        }
        PreviewDirectionArrowRenderer.render(context, preview.destinationRegion)
    }

    private fun moveSourceClipboard(source: ClipboardBuffer): ClipboardBuffer? {
        return moveSourceClipboardCache.getOrPut(source) {
            val visibleSource = ClipboardSelectionRenderer.surfaceClipboard(ClipboardSelectionRenderer.sparseClipboard(source))
            val visibleCells = visibleSource.nonAirCells()
            if (visibleCells.size > MAX_MOVE_SOURCE_CELLS) {
                null
            } else {
                ClipboardBuffer(
                    size = visibleSource.size,
                    cells = visibleCells.map { cell ->
                        cell.copy(state = Blocks.LIGHT_GRAY_STAINED_GLASS.defaultState)
                    },
                )
            }
        }
    }
}

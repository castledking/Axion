package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.tool.ClonePreviewState
import axion.client.tool.PlacementPreviewPolicy
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
    private const val DEFAULT_GHOST_ALPHA: Int = PreviewVisualPolicy.CULLED_DESTINATION_ALPHA
    private const val MOVE_SOURCE_GHOST_ALPHA: Int = PreviewVisualPolicy.CULLED_MOVE_SOURCE_ALPHA
    private const val MOVE_DESTINATION_GHOST_ALPHA: Int = PreviewVisualPolicy.CULLED_DESTINATION_ALPHA
    private const val SPARSE_DESTINATION_GHOST_ALPHA: Int = PreviewVisualPolicy.CULLED_SPARSE_DESTINATION_ALPHA
    private const val MOVE_SOURCE_GHOST_SCALE: Float = 1.0f
    // Blocks are drawn at full size. The chunked GPU preview ignores this scale
    // entirely, so any shrink only applies on the CPU fallback (which is what a
    // shader pack forces us onto) and there it pulls every block off its
    // neighbours, leaving a hairline gap along each shared edge.
    private const val DESTINATION_GHOST_SCALE: Float = 1.0f
    private const val MAX_MOVE_DETAILED_SELECTION_CELLS: Int = 4_000_000
    private const val MAX_MOVE_SOURCE_CELLS: Int = 4_000_000
    private const val MAX_MOVE_DESTINATION_GHOST_CELLS: Int = 4_000_000
    private const val MAX_CLONE_DESTINATION_GHOST_CELLS: Int = 4_000_000
    private val moveSourceClipboardCache = java.util.WeakHashMap<ClipboardBuffer, ClipboardBuffer>()
    private val moveSourceSurfaceClipboardCache = java.util.WeakHashMap<ClipboardBuffer, ClipboardBuffer>()

    fun render(context: AxionWorldRenderContext) {
        val preview = PlacementToolController.currentPreview() ?: return
        renderMoveSourceReplacement(context, preview)
        val destinationColor = when (preview.mode) {
            PlacementToolMode.CLONE -> CLONE_DESTINATION_COLOR
            PlacementToolMode.MOVE -> MOVE_DESTINATION_COLOR
        }
        val destinationSelectionClipboard = ClipboardSelectionRenderer.sparseClipboard(preview.destinationClipboardBuffer)
        val destinationGhostClipboard = ClipboardSelectionRenderer.surfaceClipboard(destinationSelectionClipboard)
        val sparseDestination = ClipboardSelectionRenderer.isSparse(
            preview.destinationRegion,
            destinationSelectionClipboard,
        )
        val detailedMovePreview = preview.mode != PlacementToolMode.MOVE ||
            destinationSelectionClipboard.nonAirCells().size <= MAX_MOVE_DETAILED_SELECTION_CELLS
        val renderDestinationGhost = detailedMovePreview && destinationSelectionClipboard.nonAirCells().size <= when (preview.mode) {
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
                    sessionTag = "placement-destination",
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
                    pulseSelection = true,
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
                    sessionTag = "placement-destination",
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
                    pulseSelection = true,
                ),
            )
        }
        PreviewDirectionArrowRenderer.render(context, preview.destinationRegion)
    }

    private fun renderMoveSourceReplacement(
        context: AxionWorldRenderContext,
        preview: ClonePreviewState,
    ) {
        if (!PlacementPreviewPolicy.shouldRenderMoveSourceReplacement(preview)) {
            return
        }

        val sourceOccupancyClipboard = ClipboardSelectionRenderer.sparseClipboard(preview.sourceClipboardBuffer)
        if (sourceOccupancyClipboard.nonAirCells().size > MAX_MOVE_SOURCE_CELLS) return
        val sourceClipboard = moveSourceClipboard(sourceOccupancyClipboard) ?: return
        val sourceSurfaceClipboard = moveSourceSurfaceClipboard(sourceOccupancyClipboard) ?: return
        BlockPreviewPipeline.renderOverlay(
            context = context,
            scene = BlockPreviewPipeline.OverlayScene(
                origins = listOf(preview.sourceRegion.minCorner()),
                // The full glass occupancy is retained as a neighbor halo so
                // vanilla removes shared faces. Only occupancy-boundary cells
                // become geometry, keeping terrain visible through the shell.
                clipboard = sourceClipboard,
                fallbackClipboard = sourceSurfaceClipboard,
                color = MOVE_SOURCE_COLOR,
                alpha = MOVE_SOURCE_GHOST_ALPHA,
                scale = MOVE_SOURCE_GHOST_SCALE,
                sessionTag = "move-source-replacement",
                forceChunked = true,
                allowChunked = true,
            ),
        )
    }

    private fun moveSourceClipboard(source: ClipboardBuffer): ClipboardBuffer? {
        val cached = moveSourceClipboardCache.getOrPut(source) {
            val selectedCells = source.nonAirCells()
            if (selectedCells.size > MAX_MOVE_SOURCE_CELLS) {
                ClipboardBuffer(size = source.size, cells = emptyList())
            } else {
                ClipboardBuffer(
                    size = source.size,
                    cells = selectedCells.map { cell ->
                        cell.copy(state = Blocks.STAINED_GLASS.lightGray().defaultState)
                    },
                )
            }
        }
        return cached.takeIf { it.cells.isNotEmpty() }
    }

    private fun moveSourceSurfaceClipboard(source: ClipboardBuffer): ClipboardBuffer? {
        val cached = moveSourceSurfaceClipboardCache.getOrPut(source) {
            ClipboardBuffer(
                size = source.size,
                cells = PreviewSurfaceTopology.retainBoundaryCells(source.nonAirCells()).map { cell ->
                    cell.copy(state = Blocks.STAINED_GLASS.lightGray().defaultState)
                },
            )
        }
        return cached.takeIf { it.cells.isNotEmpty() }
    }
}

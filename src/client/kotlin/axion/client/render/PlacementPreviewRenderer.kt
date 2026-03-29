package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.tool.PlacementToolController
import axion.client.tool.PlacementToolMode
import axion.common.model.ClipboardBuffer
import net.minecraft.block.Blocks
import java.util.WeakHashMap

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
    private val moveSourceClipboardCache = WeakHashMap<ClipboardBuffer, ClipboardBuffer>()

    fun render(context: AxionWorldRenderContext) {
        val preview = PlacementToolController.currentPreview() ?: return
        val destinationColor = when (preview.mode) {
            PlacementToolMode.CLONE -> CLONE_DESTINATION_COLOR
            PlacementToolMode.MOVE -> MOVE_DESTINATION_COLOR
        }
        val destinationSelectionClipboard = ClipboardSelectionRenderer.sparseClipboard(preview.destinationClipboardBuffer)
        val destinationGhostClipboard = ClipboardSelectionRenderer.surfaceClipboard(preview.destinationClipboardBuffer)
        val sparseDestination = ClipboardSelectionRenderer.isSparse(
            preview.destinationRegion,
            destinationSelectionClipboard,
        )
        if (preview.mode == PlacementToolMode.MOVE) {
            GhostBlockPreviewRenderer.render(
                context = context,
                clipboard = moveSourceClipboard(preview.sourceClipboardBuffer),
                origins = listOf(preview.sourceRegion.minCorner()),
                color = MOVE_SOURCE_COLOR,
                alpha = MOVE_SOURCE_GHOST_ALPHA,
                textured = true,
                scale = MOVE_SOURCE_GHOST_SCALE,
            )
        }
        if (sparseDestination) {
            ClipboardSelectionRenderer.renderSelection(
                context = context,
                origin = preview.destinationRegion.minCorner(),
                clipboard = destinationSelectionClipboard,
                outlineColor = destinationColor,
                lineWidth = LINE_WIDTH,
            )
        } else {
            PulsingCuboidRenderer.renderOutlineBox(
                context = context,
                box = SelectionBounds.regionBox(preview.destinationRegion),
                outlineColor = destinationColor,
                lineWidth = LINE_WIDTH,
            )
        }
        GhostBlockPreviewRenderer.render(
            context = context,
            clipboard = if (sparseDestination) destinationGhostClipboard else preview.destinationClipboardBuffer,
            origins = listOf(preview.destinationRegion.minCorner()),
            color = DESTINATION_GHOST_COLOR,
            alpha = when {
                sparseDestination -> SPARSE_DESTINATION_GHOST_ALPHA
                preview.mode == PlacementToolMode.MOVE -> MOVE_DESTINATION_GHOST_ALPHA
                else -> DEFAULT_GHOST_ALPHA
            },
            textured = true,
            scale = DESTINATION_GHOST_SCALE,
        )
        PreviewDirectionArrowRenderer.render(context, preview.destinationRegion)
    }

    private fun moveSourceClipboard(source: ClipboardBuffer): ClipboardBuffer {
        return moveSourceClipboardCache.getOrPut(source) {
            ClipboardBuffer(
                size = source.size,
                cells = source.cells.map { cell ->
                    cell.copy(state = Blocks.LIGHT_GRAY_STAINED_GLASS.defaultState)
                },
            )
        }
    }
}

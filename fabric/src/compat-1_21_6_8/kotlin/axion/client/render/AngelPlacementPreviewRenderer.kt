package axion.client.render

import axion.client.mode.AngelPlacementController
import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.util.math.Vec3i

/**
 * Draws the Angel Placement ghost: the single block that a right-click would put
 * into open air.
 *
 * [AngelPlacementController] resolves the target once per tick; this only turns
 * it into the one-cell clipboard that [GhostBlockPreviewRenderer] expects.
 */
object AngelPlacementPreviewRenderer {
    private const val GHOST_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val GHOST_ALPHA: Int = PreviewVisualPolicy.CULLED_DESTINATION_ALPHA

    fun render(context: AxionWorldRenderContext) {
        val ghost = AngelPlacementController.currentGhost() ?: return
        val clipboard = ClipboardBuffer(
            size = Vec3i(1, 1, 1),
            cells = listOf(ClipboardCell(offset = Vec3i(0, 0, 0), state = ghost.state)),
        )

        GhostBlockPreviewRenderer.render(
            context = context,
            clipboard = clipboard,
            origins = listOf(ghost.pos),
            fallbackClipboard = clipboard,
            color = GHOST_COLOR,
            alpha = GHOST_ALPHA,
            textured = true,
            // One block never benefits from the chunked GPU session, and giving it
            // its own session slot would churn buffers every time the ghost moves.
            allowChunked = false,
            sessionTag = "angel",
        )
    }
}

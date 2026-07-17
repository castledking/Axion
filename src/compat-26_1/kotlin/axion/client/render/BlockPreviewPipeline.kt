package axion.client.render

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.core.Vec3i
import axion.client.compat.add

object BlockPreviewPipeline {
    private const val SPARSE_OUTLINE_BUDGET: Int = 8192
    private const val LARGE_PREVIEW_CELL_THRESHOLD: Long = 1024L

    enum class SelectionStyle {
        SELECTION,
        PULSE,
    }

    data class OverlayScene(
        val origins: List<BlockPos>,
        val clipboard: axion.common.model.ClipboardBuffer,
        val color: Int,
        val alpha: Int,
        val scale: Float,
        val textured: Boolean = true,
        val sessionTag: String = "overlay",
        val forceChunked: Boolean = false,
        val allowChunked: Boolean = true,
    )

    data class SelectionScene(
        val origins: List<BlockPos>,
        val selectionClipboard: axion.common.model.ClipboardBuffer?,
        val sparse: Boolean,
        val outlineColor: Int,
        val lineWidth: Float,
        val aggregateBox: Box? = null,
        val style: SelectionStyle = SelectionStyle.SELECTION,
        val baseFillColor: Int,
        val baseAlpha: Int,
        val pulseFillColor: Int? = null,
        val pulseMinAlpha: Int = 0,
        val pulseMaxAlpha: Int = 0,
        val shellFillColor: Int = 0xFFFFFFFF.toInt(),
    )

    data class Scene(
        val origins: List<BlockPos>,
        val selectionClipboard: axion.common.model.ClipboardBuffer,
        val shellClipboard: axion.common.model.ClipboardBuffer,
        val fallbackGhostClipboard: axion.common.model.ClipboardBuffer,
        val sessionTag: String = "destination",
        val sparse: Boolean,
        val outlineColor: Int,
        val lineWidth: Float,
        val ghostColor: Int,
        val ghostAlpha: Int,
        val ghostScale: Float,
        val aggregateBox: Box? = null,
        val renderGhost: Boolean = true,
        val pulseSelection: Boolean = false,
    )

    fun renderOverlay(
        context: AxionWorldRenderContext,
        scene: OverlayScene,
    ): Boolean {
        if (scene.origins.isEmpty() || scene.clipboard.nonAirCells().isEmpty()) {
            return false
        }

        GhostBlockPreviewRenderer.render(
            context = context,
            clipboard = scene.clipboard,
            origins = scene.origins,
            color = scene.color,
            alpha = scene.alpha,
            textured = scene.textured,
            scale = scene.scale,
            sessionTag = scene.sessionTag,
            forceChunked = scene.forceChunked,
            allowChunked = scene.allowChunked,
        )
        return true
    }

    fun renderSelection(
        context: AxionWorldRenderContext,
        scene: SelectionScene,
    ): Boolean {
        if (scene.sparse && scene.selectionClipboard != null && scene.origins.isNotEmpty()) {
            return when (scene.style) {
                SelectionStyle.SELECTION -> ClipboardSelectionRenderer.renderStyledSelection(
                    context = context,
                    origins = scene.origins,
                    clipboard = scene.selectionClipboard,
                    outlineColor = scene.outlineColor,
                    lineWidth = scene.lineWidth,
                    baseFillColor = scene.baseFillColor,
                    baseAlpha = scene.baseAlpha,
                    pulseFillColor = scene.pulseFillColor,
                    pulseMinAlpha = scene.pulseMinAlpha,
                    pulseMaxAlpha = scene.pulseMaxAlpha,
                )

                SelectionStyle.PULSE -> scene.origins.any { origin ->
                    val max = origin.add(
                        scene.selectionClipboard.size.x - 1,
                        scene.selectionClipboard.size.y - 1,
                        scene.selectionClipboard.size.z - 1,
                    )
                    ClipboardSelectionRenderer.renderPulse(
                        context = context,
                        origin = origin,
                        region = axion.common.model.BlockRegion(origin, max),
                        clipboard = scene.selectionClipboard,
                        outlineColor = scene.outlineColor,
                        lineWidth = scene.lineWidth,
                        minAlpha = scene.pulseMinAlpha,
                        maxAlpha = scene.pulseMaxAlpha,
                    )
                }
            }
        }

        scene.aggregateBox?.let { box ->
            when (scene.style) {
                SelectionStyle.SELECTION -> {
                    val pulseFillColor = scene.pulseFillColor
                    if (pulseFillColor != null) {
                        PulsingCuboidRenderer.renderSelectionBox(
                            context = context,
                            box = box,
                            outlineColor = scene.outlineColor,
                            lineWidth = scene.lineWidth,
                            baseFillColor = scene.baseFillColor,
                            baseAlpha = scene.baseAlpha,
                            pulseFillColor = pulseFillColor,
                            pulseMinAlpha = scene.pulseMinAlpha,
                            pulseMaxAlpha = scene.pulseMaxAlpha,
                        )
                    } else {
                        PulsingCuboidRenderer.render(
                            context = context,
                            box = box,
                            outlineColor = scene.outlineColor,
                            lineWidth = scene.lineWidth,
                            minAlpha = scene.baseAlpha,
                            maxAlpha = scene.baseAlpha,
                        )
                    }
                }

                SelectionStyle.PULSE -> PulsingCuboidRenderer.renderShell(
                    context = context,
                    box = box,
                    outlineColor = scene.outlineColor,
                    lineWidth = scene.lineWidth,
                    minAlpha = scene.pulseMinAlpha,
                    maxAlpha = scene.pulseMaxAlpha,
                    fillColor = scene.shellFillColor,
                )
            }
            return true
        }

        return false
    }

    fun renderDestination(
        context: AxionWorldRenderContext,
        scene: Scene,
    ): Boolean {
        if (scene.origins.isEmpty()) {
            renderOutline(context, scene)
            return false
        }

        val nonAirCells = scene.shellClipboard.nonAirCells()
        if (nonAirCells.isEmpty()) {
            renderOutline(context, scene)
            return false
        }

        renderOutline(context, scene)

        if (!scene.renderGhost) {
            return true
        }

        GhostBlockPreviewRenderer.render(
            context = context,
            clipboard = scene.fallbackGhostClipboard,
            origins = scene.origins,
            color = scene.ghostColor,
            alpha = scene.ghostAlpha,
            textured = true,
            scale = scene.ghostScale,
            sessionTag = scene.sessionTag,
            forceChunked = true,
        )
        return true
    }

    private fun renderOutline(
        context: AxionWorldRenderContext,
        scene: Scene,
    ) {
        scene.aggregateBox?.let { box ->
            if (scene.pulseSelection) {
                PulsingCuboidRenderer.renderSelectionBox(
                    context = context,
                    box = box,
                    outlineColor = scene.outlineColor,
                    lineWidth = scene.lineWidth,
                    baseFillColor = 0xFFCC5656.toInt(),
                    baseAlpha = 1,
                    pulseFillColor = 0xFF7C98FF.toInt(),
                    pulseMinAlpha = 4,
                    pulseMaxAlpha = 8,
                )
            } else {
                PulsingCuboidRenderer.renderOutlineBox(
                    context = context,
                    box = box,
                    outlineColor = scene.outlineColor,
                    lineWidth = scene.lineWidth,
                )
            }
        }
    }
}

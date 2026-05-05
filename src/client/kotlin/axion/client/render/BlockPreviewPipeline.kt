package axion.client.render

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

object BlockPreviewPipeline {
    // Outline budget for the per-component path. Below this we draw individual
    // component outlines via ClipboardSelectionRenderer (Axiom-style); above it
    // we fall back to a single aggregate bounding box.
    //
    // Sized for typical accumulated magic-select selections: a brush-10 sphere
    // is ~1.3k surface cells, so a 4-click multi-pick fits comfortably inside
    // 8k. Components individually larger than MAX_VOXEL_UNION_CELLS in
    // ClipboardSelectionRenderer still fall back per-component, so the
    // aggregate budget can stay generous without making single huge selections
    // pay an O(n^2) VoxelShapes.union cost.
    private const val SPARSE_OUTLINE_BUDGET: Int = 8192

    // Route destination previews (clone etc.) through the GPU chunked
    // session path once the surface*origin count exceeds this threshold.
    // Smaller previews stay on PreviewShellBlockRenderer which has
    // correct inner-face culling via TemplateBlockRenderView and is fast
    // enough for small meshes.
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
        // Stable session slot for chunked GPU caching. Distinct slots prevent
        // sessions from leaking GPU buffers when the clipboard rebuilds.
        val sessionTag: String = "overlay",
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
        // Stable session slot for chunked GPU caching (see OverlayScene comment).
        val sessionTag: String = "destination",
        val sparse: Boolean,
        val outlineColor: Int,
        val lineWidth: Float,
        val ghostColor: Int,
        val ghostAlpha: Int,
        val ghostScale: Float,
        val aggregateBox: Box? = null,
        val renderGhost: Boolean = true,
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

        // For large previews (e.g. cloning a whole church), skip the
        // per-frame CPU tessellation path (PreviewShellBlockRenderer) and
        // route straight through the GPU chunked session. That path uploads
        // each 16³ section once to a persistent GpuBuffer and redraws from
        // it every frame — O(visibleSections) instead of O(surfaceCells).
        //
        // Threshold mirrors GhostBlockPreviewRenderer.CHUNKED_PATH_CELL_THRESHOLD.
        val totalCells = nonAirCells.size.toLong() * scene.origins.size.toLong()
        if (totalCells > LARGE_PREVIEW_CELL_THRESHOLD) {
            GhostBlockPreviewRenderer.render(
                context = context,
                clipboard = scene.fallbackGhostClipboard,
                origins = scene.origins,
                color = scene.ghostColor,
                alpha = scene.ghostAlpha,
                textured = true,
                scale = scene.ghostScale,
                sessionTag = scene.sessionTag,
            )
            return true
        }

        val renderedShell = PreviewShellBlockRenderer.render(
            context = context,
            clipboard = scene.shellClipboard,
            origins = scene.origins,
            color = scene.ghostColor,
            alpha = scene.ghostAlpha,
            scale = scene.ghostScale,
        )
        if (!renderedShell) {
            GhostBlockPreviewRenderer.render(
                context = context,
                clipboard = scene.fallbackGhostClipboard,
                origins = scene.origins,
                color = scene.ghostColor,
                alpha = scene.ghostAlpha,
                textured = true,
                scale = scene.ghostScale,
                sessionTag = scene.sessionTag,
            )
        }
        return true
    }

    private fun renderOutline(
        context: AxionWorldRenderContext,
        scene: Scene,
    ) {
        // The placement-destination "global outline" should always be a single
        // axis-aligned bounding box around the entire destination region —
        // never per-block voxel outlines. A per-block outline:
        //   * z-fights with the actual blocks underneath when the destination
        //     overlaps existing geometry,
        //   * traces around air gaps (sparse selections), making the outline
        //     wrap around the shape instead of behaving like a single
        //     selection box,
        //   * desyncs visually from the block preview when the chunked
        //     fast-path translates the preview but the per-block outline is
        //     rebuilt from the new origin every frame.
        //
        // SuppressWarnings: the legacy [SPARSE_OUTLINE_BUDGET] /
        // [PreviewRegionOutlineRenderer] / sparse-clipboard parameters are
        // intentionally ignored here.
        scene.aggregateBox?.let { box ->
            PulsingCuboidRenderer.renderOutlineBox(
                context = context,
                box = box,
                outlineColor = scene.outlineColor,
                lineWidth = scene.lineWidth,
            )
        }
    }
}

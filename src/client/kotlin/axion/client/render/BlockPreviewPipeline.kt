package axion.client.render

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

object BlockPreviewPipeline {
    private val LOGGER = org.slf4j.LoggerFactory.getLogger("AxionPreviewPipeline")
    private var diagCounter = 0
    private const val SPARSE_OUTLINE_BUDGET: Int = 512
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
            if (diagCounter++ % 120 == 0) {
                LOGGER.warn("[Axion] renderDestination: SKIP origins empty, sparse={}", scene.sparse)
            }
            renderOutline(context, scene)
            return false
        }

        val nonAirCells = scene.shellClipboard.nonAirCells()
        if (nonAirCells.isEmpty()) {
            if (diagCounter++ % 120 == 0) {
                LOGGER.warn("[Axion] renderDestination: SKIP nonAirCells empty")
            }
            renderOutline(context, scene)
            return false
        }

        renderOutline(context, scene)

        if (!scene.renderGhost) {
            if (diagCounter++ % 120 == 0) {
                LOGGER.warn("[Axion] renderDestination: renderGhost=false, origins={}, nonAirCells={}", scene.origins.size, nonAirCells.size)
            }
            return true
        }

        if (diagCounter++ % 120 == 0) {
            LOGGER.warn("[Axion] renderDestination: RENDERING ghost, origins={}, nonAirCells={}", scene.origins.size, nonAirCells.size)
        }

        val renderedShell = PreviewShellBlockRenderer.render(
            context = context,
            clipboard = scene.shellClipboard,
            origins = scene.origins,
            color = scene.ghostColor,
            alpha = scene.ghostAlpha,
        )
        if (!renderedShell) {
            if (diagCounter++ % 120 == 0) {
                LOGGER.warn("[Axion] renderDestination: shell failed, falling back to GhostBlockPreviewRenderer")
            }
            GhostBlockPreviewRenderer.render(
                context = context,
                clipboard = scene.fallbackGhostClipboard,
                origins = scene.origins,
                color = scene.ghostColor,
                alpha = scene.ghostAlpha,
                textured = true,
                scale = scene.ghostScale,
            )
        }
        return true
    }

    private fun renderOutline(
        context: AxionWorldRenderContext,
        scene: Scene,
    ) {
        if (scene.sparse) {
            val totalCells = scene.selectionClipboard.nonAirCells().size.toLong() * scene.origins.size.toLong()
            val withinBudget = totalCells <= SPARSE_OUTLINE_BUDGET
            if (withinBudget && !PreviewRegionOutlineRenderer.render(
                    context = context,
                    clipboard = scene.selectionClipboard,
                    origins = scene.origins,
                    outlineColor = scene.outlineColor,
                    lineWidth = scene.lineWidth,
                )
            ) {
                ClipboardSelectionRenderer.renderSelection(
                    context = context,
                    origins = scene.origins,
                    clipboard = scene.selectionClipboard,
                    outlineColor = scene.outlineColor,
                    lineWidth = scene.lineWidth,
                )
            }
            if (!withinBudget) {
                scene.aggregateBox?.let { box ->
                    PulsingCuboidRenderer.renderOutlineBox(
                        context = context,
                        box = box,
                        outlineColor = scene.outlineColor,
                        lineWidth = scene.lineWidth,
                    )
                }
            }
        } else {
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
}

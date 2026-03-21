package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.tool.RepeatRegionPreview
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object RepeatPreviewRenderer {
    private const val MAX_REGION_OUTLINES: Int = 96
    private const val SOURCE_SELECTION_COLOR: Int = 0xFFFFFFFF.toInt()

    fun render(
        context: WorldRenderContext,
        preview: RepeatRegionPreview,
        destinationColor: Int,
        lineWidth: Float,
    ) {
        val renderedSparseSource = ClipboardSelectionRenderer.renderPulse(
            context = context,
            origin = preview.sourceRegion.minCorner(),
            region = preview.sourceRegion,
            clipboard = preview.clipboardBuffer,
            outlineColor = SOURCE_SELECTION_COLOR,
            lineWidth = lineWidth,
            minAlpha = 0,
            maxAlpha = 166,
        )
        if (!renderedSparseSource) {
            PulsingCuboidRenderer.renderShell(
                context = context,
                box = SelectionBounds.regionBox(preview.sourceRegion),
                outlineColor = SOURCE_SELECTION_COLOR,
                lineWidth = lineWidth,
                minAlpha = 0,
                maxAlpha = 166,
            )
        }
        val destinationRegions = RepeatPreviewLayout.destinationRegions(
            sourceRegion = preview.sourceRegion,
            step = preview.step,
            repeatCount = preview.repeatCount,
            maxRegions = MAX_REGION_OUTLINES,
        )
        destinationRegions.forEach { region ->
            PulsingCuboidRenderer.render(
                context = context,
                box = SelectionBounds.outlineBox(SelectionBounds.regionBox(region)),
                outlineColor = destinationColor,
                lineWidth = lineWidth,
            )
        }
        if (preview.repeatCount > destinationRegions.size) {
            RepeatPreviewLayout.aggregateRegion(
                sourceRegion = preview.sourceRegion,
                step = preview.step,
                startIndex = destinationRegions.size + 1,
                endIndex = preview.repeatCount,
            )?.let { hiddenRegion ->
                PulsingCuboidRenderer.render(
                    context = context,
                    box = SelectionBounds.outlineBox(SelectionBounds.regionBox(hiddenRegion)),
                    outlineColor = destinationColor,
                    lineWidth = lineWidth,
                )
            }
        }

        val nonAirCells = preview.clipboardBuffer.nonAirCells()
        if (nonAirCells.isEmpty()) {
            return
        }
        val maxGhostOrigins = maxOf(1, GhostBlockPreviewRenderer.maxOriginsFor(nonAirCells.size))
        val ghostOrigins = destinationRegions
            .asSequence()
            .take(maxGhostOrigins)
            .map { it.minCorner() }
            .toList()
        GhostBlockPreviewRenderer.render(
            context = context,
            clipboard = preview.clipboardBuffer,
            origins = ghostOrigins,
            textured = true,
        )
        val arrowRegion = RepeatPreviewLayout.aggregateRegion(
            sourceRegion = preview.sourceRegion,
            step = preview.step,
            startIndex = preview.repeatCount,
            endIndex = preview.repeatCount,
        ) ?: preview.sourceRegion
        PreviewDirectionArrowRenderer.render(context, arrowRegion)
    }
}

package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.tool.RepeatRegionPreview
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object RepeatPreviewRenderer {
    private const val MAX_REGION_OUTLINES: Int = 96

    fun render(
        context: WorldRenderContext,
        preview: RepeatRegionPreview,
        destinationColor: Int,
        lineWidth: Float,
    ) {
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
        )
    }
}

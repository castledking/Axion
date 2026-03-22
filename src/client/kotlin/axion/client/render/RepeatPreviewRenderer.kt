package axion.client.render

import axion.client.network.BlockWrite
import axion.client.network.LocalWritePlanner
import axion.client.selection.SelectionBounds
import axion.client.tool.RegionRepeatPlacementService
import axion.client.tool.RepeatRegionPreview
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos

object RepeatPreviewRenderer {
    private const val MAX_REGION_OUTLINES: Int = 96
    private const val MAX_COLLISION_PULSE_BLOCKS: Int = 2048
    private const val SOURCE_SELECTION_COLOR: Int = 0xFFFFFFFF.toInt()
    private val writePlanner = LocalWritePlanner()

    fun render(
        context: WorldRenderContext,
        preview: RepeatRegionPreview,
        mode: RegionRepeatPlacementService.Mode,
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

        if (mode == RegionRepeatPlacementService.Mode.SMEAR) {
            renderCollisionAware(
                context = context,
                preview = preview,
                mode = mode,
                destinationColor = destinationColor,
                lineWidth = lineWidth,
            )
            return
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
        renderArrow(context, preview)
    }

    private fun renderCollisionAware(
        context: WorldRenderContext,
        preview: RepeatRegionPreview,
        mode: RegionRepeatPlacementService.Mode,
        destinationColor: Int,
        lineWidth: Float,
    ) {
        val world = MinecraftClient.getInstance().world ?: return
        val finalWrites = linkedMapOf<BlockPos, BlockWrite>()
        writePlanner.plan(world, RegionRepeatPlacementService.toOperation(preview, mode)).writes.forEach { write ->
            finalWrites[write.pos.toImmutable()] = write
        }
        val renderedWrites = finalWrites.values.filterNot { it.state.isAir }
        if (renderedWrites.isNotEmpty()) {
            ClipboardSelectionRenderer.renderPulsePositions(
                context = context,
                positions = renderedWrites.asSequence().map { it.pos }.take(MAX_COLLISION_PULSE_BLOCKS).toList(),
                outlineColor = destinationColor,
                lineWidth = lineWidth,
                minAlpha = 0,
                maxAlpha = 110,
            )
            GhostBlockPreviewRenderer.renderWrites(
                context = context,
                writes = renderedWrites,
                color = destinationColor,
                textured = true,
            )
        }
        renderArrow(context, preview)
    }

    private fun renderArrow(
        context: WorldRenderContext,
        preview: RepeatRegionPreview,
    ) {
        val arrowRegion = RepeatPreviewLayout.aggregateRegion(
            sourceRegion = preview.sourceRegion,
            step = preview.step,
            startIndex = preview.repeatCount,
            endIndex = preview.repeatCount,
        ) ?: preview.sourceRegion
        PreviewDirectionArrowRenderer.render(context, arrowRegion)
    }
}

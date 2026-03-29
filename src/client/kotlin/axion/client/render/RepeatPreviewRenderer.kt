package axion.client.render

import axion.client.network.BlockWrite
import axion.client.network.LocalWritePlanner
import axion.client.selection.SelectionBounds
import axion.client.tool.RegionRepeatPlacementService
import axion.client.tool.RepeatRegionPreview
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos

object RepeatPreviewRenderer {
    private const val MAX_REGION_OUTLINES: Int = 96
    private const val MAX_COLLISION_PULSE_BLOCKS: Int = 2048
    private const val DESTINATION_GHOST_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val DEFAULT_GHOST_ALPHA: Int = 70
    private const val SPARSE_GHOST_ALPHA: Int = 180
    private const val GHOST_SCALE: Float = 0.985f
    private const val SOURCE_SELECTION_COLOR: Int = 0xFFFFFFFF.toInt()
    private val writePlanner = LocalWritePlanner()

    fun render(
        context: AxionWorldRenderContext,
        preview: RepeatRegionPreview,
        mode: RegionRepeatPlacementService.Mode,
        destinationColor: Int,
        lineWidth: Float,
    ) {
        if (mode != RegionRepeatPlacementService.Mode.STACK && mode != RegionRepeatPlacementService.Mode.SMEAR) {
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

        val selectionClipboard = ClipboardSelectionRenderer.sparseClipboard(preview.clipboardBuffer)
        val destinationGhostClipboard = ClipboardSelectionRenderer.surfaceClipboard(preview.clipboardBuffer)
        val sparseDestination = ClipboardSelectionRenderer.isSparse(preview.sourceRegion, selectionClipboard)
        RepeatPreviewLayout.aggregateRegion(
            sourceRegion = preview.sourceRegion,
            step = preview.step,
            startIndex = 0,
            endIndex = preview.repeatCount,
        )?.let { aggregateRegion ->
            val aggregateBox = SelectionBounds.regionBox(aggregateRegion)

            val nonAirCells = preview.clipboardBuffer.nonAirCells()
            if (nonAirCells.isNotEmpty()) {
                val maxGhostOrigins = maxOf(1, GhostBlockPreviewRenderer.maxOriginsFor(nonAirCells.size))
                val ghostOrigins = RepeatPreviewLayout.destinationRegions(
                    sourceRegion = preview.sourceRegion,
                    step = preview.step,
                    repeatCount = preview.repeatCount,
                    maxRegions = MAX_REGION_OUTLINES,
                )
                    .asSequence()
                    .take(maxGhostOrigins)
                    .map { it.minCorner() }
                    .toList()
                if (sparseDestination) {
                    ClipboardSelectionRenderer.renderSelection(
                        context = context,
                        origins = ghostOrigins,
                        clipboard = selectionClipboard,
                        outlineColor = destinationColor,
                        lineWidth = lineWidth,
                    )
                } else {
                    PulsingCuboidRenderer.renderOutlineBox(
                        context = context,
                        box = aggregateBox,
                        outlineColor = destinationColor,
                        lineWidth = lineWidth,
                    )
                }
                GhostBlockPreviewRenderer.render(
                    context = context,
                    clipboard = if (sparseDestination) destinationGhostClipboard else preview.clipboardBuffer,
                    origins = ghostOrigins,
                    color = DESTINATION_GHOST_COLOR,
                    alpha = if (sparseDestination) SPARSE_GHOST_ALPHA else DEFAULT_GHOST_ALPHA,
                    textured = true,
                    scale = GHOST_SCALE,
                )
            } else if (!sparseDestination) {
                PulsingCuboidRenderer.renderOutlineBox(
                    context = context,
                    box = aggregateBox,
                    outlineColor = destinationColor,
                    lineWidth = lineWidth,
                )
            } else {
                val sparseOrigins = RepeatPreviewLayout.destinationRegions(
                    sourceRegion = preview.sourceRegion,
                    step = preview.step,
                    repeatCount = preview.repeatCount,
                    maxRegions = MAX_REGION_OUTLINES,
                ).map { it.minCorner() }
                ClipboardSelectionRenderer.renderSelection(
                    context = context,
                    origins = sparseOrigins,
                    clipboard = selectionClipboard,
                    outlineColor = destinationColor,
                    lineWidth = lineWidth,
                )
            }
        }
        renderArrow(context, preview)
    }

    private fun renderCollisionAware(
        context: AxionWorldRenderContext,
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
            val selectionClipboard = ClipboardSelectionRenderer.sparseClipboard(preview.clipboardBuffer)
            val sparseDestination = ClipboardSelectionRenderer.isSparse(preview.sourceRegion, selectionClipboard)
            if (!sparseDestination) {
                RepeatPreviewLayout.aggregateRegion(
                    sourceRegion = preview.sourceRegion,
                    step = preview.step,
                    startIndex = 0,
                    endIndex = preview.repeatCount,
                )?.let { aggregateRegion ->
                    PulsingCuboidRenderer.renderOutlineBox(
                        context = context,
                        box = SelectionBounds.regionBox(aggregateRegion),
                        outlineColor = destinationColor,
                        lineWidth = lineWidth,
                    )
                }
            }
            ClipboardSelectionRenderer.renderPulsePositions(
                context = context,
                positions = renderedWrites.asSequence().map { it.pos }.take(MAX_COLLISION_PULSE_BLOCKS).toList(),
                outlineColor = destinationColor,
                lineWidth = lineWidth,
                minAlpha = 16,
                maxAlpha = 34,
            )
            GhostBlockPreviewRenderer.renderWrites(
                context = context,
                writes = renderedWrites,
                color = destinationColor,
                textured = true,
                scale = GHOST_SCALE,
            )
        }
        renderArrow(context, preview)
    }

    private fun renderArrow(
        context: AxionWorldRenderContext,
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

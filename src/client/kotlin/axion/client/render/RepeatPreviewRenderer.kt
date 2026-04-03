package axion.client.render

import axion.client.network.BlockWrite
import axion.client.network.LocalWritePlanner
import axion.client.selection.SelectionBounds
import axion.client.tool.RepeatPreviewSegment
import axion.client.tool.RegionRepeatPlacementService
import axion.client.tool.RepeatRegionPreview
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

object RepeatPreviewRenderer {
    private const val MAX_REGION_OUTLINES: Int = 96
    private const val MAX_COLLISION_PULSE_BLOCKS: Int = 2048
    private const val DESTINATION_GHOST_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val DEFAULT_GHOST_ALPHA: Int = 156
    private const val SPARSE_GHOST_ALPHA: Int = 228
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
            val renderedSparseSource = BlockPreviewPipeline.renderSelection(
                context = context,
                scene = BlockPreviewPipeline.SelectionScene(
                    origins = listOf(preview.sourceRegion.minCorner()),
                    selectionClipboard = preview.clipboardBuffer,
                    sparse = ClipboardSelectionRenderer.isSparse(preview.sourceRegion, preview.clipboardBuffer),
                    outlineColor = SOURCE_SELECTION_COLOR,
                    lineWidth = lineWidth,
                    aggregateBox = SelectionBounds.regionBox(preview.sourceRegion),
                    style = BlockPreviewPipeline.SelectionStyle.PULSE,
                    baseFillColor = SOURCE_SELECTION_COLOR,
                    baseAlpha = 0,
                    pulseFillColor = null,
                    pulseMinAlpha = 0,
                    pulseMaxAlpha = 166,
                    shellFillColor = SOURCE_SELECTION_COLOR,
                ),
            )
            if (!renderedSparseSource) {
                BlockPreviewPipeline.renderSelection(
                    context = context,
                    scene = BlockPreviewPipeline.SelectionScene(
                        origins = emptyList(),
                        selectionClipboard = null,
                        sparse = false,
                        outlineColor = SOURCE_SELECTION_COLOR,
                        lineWidth = lineWidth,
                        aggregateBox = SelectionBounds.regionBox(preview.sourceRegion),
                        style = BlockPreviewPipeline.SelectionStyle.PULSE,
                        baseFillColor = SOURCE_SELECTION_COLOR,
                        baseAlpha = 0,
                        pulseFillColor = null,
                        pulseMinAlpha = 0,
                        pulseMaxAlpha = 166,
                        shellFillColor = SOURCE_SELECTION_COLOR,
                    ),
                )
            }
        }

        renderCommittedSegments(
            context = context,
            committedSegments = preview.committedSegments,
            destinationColor = destinationColor,
            lineWidth = lineWidth,
        )
        renderStandardRepeat(
            context = context,
            preview = preview,
            destinationColor = destinationColor,
            lineWidth = lineWidth,
        )
        renderArrow(context, preview)
    }

    private fun renderCommittedSegments(
        context: AxionWorldRenderContext,
        committedSegments: List<RepeatPreviewSegment>,
        destinationColor: Int,
        lineWidth: Float,
    ) {
        committedSegments.forEach { segment ->
            renderRepeatSegment(
                context = context,
                sourceRegion = segment.sourceRegion,
                clipboardBuffer = segment.clipboardBuffer,
                step = segment.step,
                repeatCount = segment.repeatCount,
                destinationColor = destinationColor,
                lineWidth = lineWidth,
            )
        }
    }

    private fun renderStandardRepeat(
        context: AxionWorldRenderContext,
        preview: RepeatRegionPreview,
        destinationColor: Int,
        lineWidth: Float,
    ) {
        renderRepeatSegment(
            context = context,
            sourceRegion = preview.sourceRegion,
            clipboardBuffer = preview.clipboardBuffer,
            step = preview.step,
            repeatCount = preview.repeatCount,
            destinationColor = destinationColor,
            lineWidth = lineWidth,
        )
    }

    private fun renderRepeatSegment(
        context: AxionWorldRenderContext,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        step: Vec3i,
        repeatCount: Int,
        destinationColor: Int,
        lineWidth: Float,
    ) {
        val selectionClipboard = ClipboardSelectionRenderer.sparseClipboard(clipboardBuffer)
        val destinationGhostClipboard = ClipboardSelectionRenderer.surfaceClipboard(selectionClipboard)
        val sparseDestination = ClipboardSelectionRenderer.isSparse(sourceRegion, selectionClipboard)
        val ghostClipboard = destinationGhostClipboard
        RepeatPreviewLayout.aggregateRegion(
            sourceRegion = sourceRegion,
            step = step,
            startIndex = 0,
            endIndex = repeatCount,
        )?.let { aggregateRegion ->
            val aggregateBox = SelectionBounds.regionBox(aggregateRegion)
            val destinationRegions = RepeatPreviewLayout.destinationRegions(
                sourceRegion = sourceRegion,
                step = step,
                repeatCount = repeatCount,
                maxRegions = MAX_REGION_OUTLINES,
            )

            val nonAirCells = ghostClipboard.nonAirCells()
            if (nonAirCells.isNotEmpty()) {
                val maxGhostOrigins = maxOf(1, GhostBlockPreviewRenderer.maxOriginsFor(nonAirCells.size))
                val canRenderAllGhostOrigins = repeatCount <= maxGhostOrigins
                val ghostOrigins = if (canRenderAllGhostOrigins) {
                    destinationRegions
                        .asSequence()
                        .take(maxGhostOrigins)
                        .map { it.minCorner() }
                        .toList()
                } else {
                    emptyList()
                }
                BlockPreviewPipeline.renderDestination(
                    context = context,
                    scene = BlockPreviewPipeline.Scene(
                        origins = ghostOrigins,
                        selectionClipboard = selectionClipboard,
                        shellClipboard = clipboardBuffer,
                        fallbackGhostClipboard = ghostClipboard,
                        sparse = sparseDestination,
                        outlineColor = destinationColor,
                        lineWidth = lineWidth,
                        ghostColor = DESTINATION_GHOST_COLOR,
                        ghostAlpha = if (sparseDestination) SPARSE_GHOST_ALPHA else DEFAULT_GHOST_ALPHA,
                        ghostScale = GHOST_SCALE,
                        aggregateBox = aggregateBox,
                        renderGhost = canRenderAllGhostOrigins,
                    ),
                )
            } else if (!sparseDestination) {
                BlockPreviewPipeline.renderDestination(
                    context = context,
                    scene = BlockPreviewPipeline.Scene(
                        origins = emptyList(),
                        selectionClipboard = selectionClipboard,
                        shellClipboard = clipboardBuffer,
                        fallbackGhostClipboard = ghostClipboard,
                        sparse = false,
                        outlineColor = destinationColor,
                        lineWidth = lineWidth,
                        ghostColor = DESTINATION_GHOST_COLOR,
                        ghostAlpha = DEFAULT_GHOST_ALPHA,
                        ghostScale = GHOST_SCALE,
                        aggregateBox = aggregateBox,
                        renderGhost = false,
                    ),
                )
            } else {
                val sparseOrigins = destinationRegions.map { it.minCorner() }
                BlockPreviewPipeline.renderDestination(
                    context = context,
                    scene = BlockPreviewPipeline.Scene(
                        origins = sparseOrigins,
                        selectionClipboard = selectionClipboard,
                        shellClipboard = clipboardBuffer,
                        fallbackGhostClipboard = ghostClipboard,
                        sparse = true,
                        outlineColor = destinationColor,
                        lineWidth = lineWidth,
                        ghostColor = DESTINATION_GHOST_COLOR,
                        ghostAlpha = SPARSE_GHOST_ALPHA,
                        ghostScale = GHOST_SCALE,
                        aggregateBox = aggregateBox,
                        renderGhost = false,
                    ),
                )
            }
        }
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

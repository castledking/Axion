package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.tool.RegionRepeatPlacementService
import axion.client.tool.RepeatRegionPreview
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

object RepeatPreviewRenderer {
    private const val MAX_REGION_OUTLINES: Int = 96
    private const val DESTINATION_GHOST_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val DEFAULT_GHOST_ALPHA: Int = 156
    private const val SPARSE_GHOST_ALPHA: Int = 228
    private const val GHOST_SCALE: Float = 0.985f
    private const val SOURCE_SELECTION_COLOR: Int = 0xFFFFFFFF.toInt()

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

        val globalAggregate = if (mode == RegionRepeatPlacementService.Mode.SMEAR) {
            aggregateRegionForOffsets(
                sourceRegion = preview.sourceRegion,
                offsets = listOf(Vec3i.ZERO) + RegionRepeatPlacementService.smearOffsets(preview.step, preview.repeatCount),
            )
        } else {
            RepeatPreviewLayout.globalAggregateRegion(
                segments = preview.committedSegments,
                activeSourceRegion = preview.sourceRegion,
                activeStep = preview.step,
                activeRepeatCount = preview.repeatCount,
            )
        }
        val globalAggregateBox = globalAggregate?.let { SelectionBounds.regionBox(it) }

        if (globalAggregateBox != null) {
            PulsingCuboidRenderer.renderOutlineBox(
                context = context,
                box = globalAggregateBox,
                outlineColor = destinationColor,
                lineWidth = lineWidth,
            )
        }

        renderStandardRepeat(
            context = context,
            preview = preview,
            destinationColor = destinationColor,
            lineWidth = lineWidth,
            renderOutline = false,
            includeSourceOrigin = preview.committedSegments.isNotEmpty(),
            mode = mode,
        )
        renderArrow(context, preview)
    }

    private fun renderStandardRepeat(
        context: AxionWorldRenderContext,
        preview: RepeatRegionPreview,
        destinationColor: Int,
        lineWidth: Float,
        renderOutline: Boolean = true,
        includeSourceOrigin: Boolean = false,
        mode: RegionRepeatPlacementService.Mode,
    ) {
        renderRepeatSegment(
            context = context,
            sourceRegion = preview.sourceRegion,
            clipboardBuffer = preview.clipboardBuffer,
            step = preview.step,
            repeatCount = preview.repeatCount,
            destinationColor = destinationColor,
            lineWidth = lineWidth,
            renderOutline = renderOutline,
            includeSourceOrigin = includeSourceOrigin,
            mode = mode,
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
        forceAggregateOutline: Boolean = false,
        renderOutline: Boolean = true,
        includeSourceOrigin: Boolean = false,
        mode: RegionRepeatPlacementService.Mode,
    ) {
        val selectionClipboard = ClipboardSelectionRenderer.sparseClipboard(clipboardBuffer)
        val destinationGhostClipboard = ClipboardSelectionRenderer.surfaceClipboard(selectionClipboard)
        val sparseDestination = ClipboardSelectionRenderer.isSparse(sourceRegion, selectionClipboard)
        val ghostClipboard = destinationGhostClipboard
        val smearOffsets = if (mode == RegionRepeatPlacementService.Mode.SMEAR) {
            RegionRepeatPlacementService.smearOffsets(step, repeatCount)
        } else {
            emptyList()
        }
        val aggregateRegion = if (mode == RegionRepeatPlacementService.Mode.SMEAR) {
            aggregateRegionForOffsets(sourceRegion, listOf(Vec3i.ZERO) + smearOffsets)
        } else {
            RepeatPreviewLayout.aggregateRegion(
                sourceRegion = sourceRegion,
                step = step,
                startIndex = 0,
                endIndex = repeatCount,
            )
        }

        aggregateRegion?.let {
            val aggregateBox = if (renderOutline) SelectionBounds.regionBox(aggregateRegion) else null
            val destinationRegions = if (!forceAggregateOutline) {
                if (mode == RegionRepeatPlacementService.Mode.SMEAR) {
                    destinationRegionsForOffsets(sourceRegion, smearOffsets.take(MAX_REGION_OUTLINES))
                } else {
                    RepeatPreviewLayout.destinationRegions(
                        sourceRegion = sourceRegion,
                        step = step,
                        repeatCount = repeatCount,
                        maxRegions = MAX_REGION_OUTLINES,
                    )
                }
            } else {
                emptyList()
            }

            val nonAirCells = ghostClipboard.nonAirCells()
            if (nonAirCells.isNotEmpty()) {
                val maxLegacyGhostOrigins = maxOf(1, GhostBlockPreviewRenderer.maxOriginsFor(nonAirCells.size))
                val canRenderAllGhostOrigins = repeatCount <= maxLegacyGhostOrigins
                val baseGhostOrigins = if (forceAggregateOutline) {
                    if (mode == RegionRepeatPlacementService.Mode.SMEAR) {
                        destinationRegionsForOffsets(sourceRegion, smearOffsets).map { it.minCorner() }
                    } else {
                        RepeatPreviewLayout.destinationRegions(
                            sourceRegion = sourceRegion,
                            step = step,
                            repeatCount = repeatCount,
                            maxRegions = repeatCount,
                        ).map { it.minCorner() }
                    }
                } else {
                    if (mode == RegionRepeatPlacementService.Mode.SMEAR) {
                        destinationRegionsForOffsets(sourceRegion, smearOffsets).map { it.minCorner() }
                    } else {
                        RepeatPreviewLayout.destinationRegions(
                            sourceRegion = sourceRegion,
                            step = step,
                            repeatCount = repeatCount,
                            maxRegions = repeatCount,
                        ).map { it.minCorner() }
                    }
                }
                val ghostOrigins = if (includeSourceOrigin) {
                    listOf(sourceRegion.normalized().minCorner()) + baseGhostOrigins
                } else {
                    baseGhostOrigins
                }
                BlockPreviewPipeline.renderDestination(
                    context = context,
                    scene = BlockPreviewPipeline.Scene(
                        origins = ghostOrigins,
                        selectionClipboard = selectionClipboard,
                        shellClipboard = clipboardBuffer,
                        fallbackGhostClipboard = ghostClipboard,
                        sparse = if (!canRenderAllGhostOrigins) false else sparseDestination,
                        outlineColor = destinationColor,
                        lineWidth = lineWidth,
                        ghostColor = DESTINATION_GHOST_COLOR,
                        ghostAlpha = if (sparseDestination) SPARSE_GHOST_ALPHA else DEFAULT_GHOST_ALPHA,
                        ghostScale = GHOST_SCALE,
                        aggregateBox = aggregateBox,
                        renderGhost = true,
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
                val sparseOrigins = if (forceAggregateOutline) emptyList() else destinationRegions.map { it.minCorner() }
                BlockPreviewPipeline.renderDestination(
                    context = context,
                    scene = BlockPreviewPipeline.Scene(
                        origins = sparseOrigins,
                        selectionClipboard = selectionClipboard,
                        shellClipboard = clipboardBuffer,
                        fallbackGhostClipboard = ghostClipboard,
                        sparse = !forceAggregateOutline,
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
        val arrowRegion = preview.sourceRegion.offset(preview.step).normalized()
        PreviewDirectionArrowRenderer.render(context, arrowRegion)
    }

    private fun destinationRegionsForOffsets(
        sourceRegion: BlockRegion,
        offsets: List<Vec3i>,
    ): List<BlockRegion> {
        val normalized = sourceRegion.normalized()
        return offsets.map { offset -> normalized.offset(offset).normalized() }
    }

    private fun aggregateRegionForOffsets(
        sourceRegion: BlockRegion,
        offsets: List<Vec3i>,
    ): BlockRegion? {
        if (offsets.isEmpty()) {
            return null
        }
        val regions = destinationRegionsForOffsets(sourceRegion, offsets)
        val min = BlockPos(
            regions.minOf { it.minCorner().x },
            regions.minOf { it.minCorner().y },
            regions.minOf { it.minCorner().z },
        )
        val max = BlockPos(
            regions.maxOf { it.maxCorner().x },
            regions.maxOf { it.maxCorner().y },
            regions.maxOf { it.maxCorner().z },
        )
        return BlockRegion(min, max).normalized()
    }
}

package axion.client.render

import axion.client.network.BlockWrite
import axion.client.network.LocalWritePlanner
import axion.client.selection.SelectionBounds
import axion.client.tool.RegionRepeatPlacementService
import axion.client.tool.RepeatRegionPreview
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import net.minecraft.world.World

object RepeatPreviewRenderer {
    private const val MAX_REGION_OUTLINES: Int = 96
    private const val MAX_COLLISION_PULSE_BLOCKS: Int = 2048
    private const val MAX_DESTINATION_OCCUPANCY_CELLS: Long = 4_000_000L
    private const val DESTINATION_GHOST_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val DEFAULT_GHOST_ALPHA: Int = PreviewVisualPolicy.DESTINATION_ALPHA
    private const val SPARSE_GHOST_ALPHA: Int = PreviewVisualPolicy.SPARSE_DESTINATION_ALPHA
    // Full size: see DESTINATION_GHOST_SCALE in PlacementPreviewRenderer -- a
    // per-block shrink only takes effect on the CPU fallback path and shows up
    // there as seams between neighbouring preview blocks.
    private const val GHOST_SCALE: Float = 1.0f
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
                        ),
                )
            }
        }

        if (mode == RegionRepeatPlacementService.Mode.SMEAR) {
            renderClippedSmear(
                context = context,
                preview = preview,
                destinationColor = destinationColor,
                lineWidth = lineWidth,
            )
            renderArrow(context, preview)
            return
        }

        // Compute a single global aggregate outline across all committed segments + active segment
        val globalAggregate = RepeatPreviewLayout.globalAggregateRegion(
            segments = preview.committedSegments,
            activeSourceRegion = preview.sourceRegion,
            activeStep = preview.step,
            activeRepeatCount = preview.repeatCount,
        )
        val globalAggregateBox = globalAggregate?.let { SelectionBounds.regionBox(it) }

        // Render the single global outline
        if (globalAggregateBox != null) {
            PulsingCuboidRenderer.renderOutlineBox(
                context = context,
                box = globalAggregateBox,
                outlineColor = destinationColor,
                lineWidth = lineWidth,
            )
        }

        // Active preview's folded clipboard already contains all committed segment
        // blocks merged into one buffer, so we only render the active segment.
        // Rendering committed segments separately would cause internal face bleed
        // because each render pass has its own face-culling context.
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

    private fun renderClippedSmear(
        context: AxionWorldRenderContext,
        preview: RepeatRegionPreview,
        destinationColor: Int,
        lineWidth: Float,
    ) {
        val occupiedCellCount = preview.clipboardBuffer.nonAirCells().size
        if (!isDestinationGhostWithinBudget(occupiedCellCount, preview.repeatCount.toLong())) {
            aggregateRegionForOffsets(
                sourceRegion = preview.sourceRegion,
                offsets = listOf(Vec3i.ZERO, preview.step),
            )?.let { aggregateRegion ->
                PulsingCuboidRenderer.renderOutlineBox(
                    context = context,
                    box = SelectionBounds.regionBox(aggregateRegion),
                    outlineColor = destinationColor,
                    lineWidth = lineWidth,
                )
            }
            return
        }

        val world = MinecraftClient.getInstance().world ?: return
        val layout = clippedSmearLayout(
            world = world,
            sourceRegion = preview.sourceRegion,
            clipboardBuffer = preview.clipboardBuffer,
            step = preview.step,
            repeatCount = preview.repeatCount,
        ) ?: return
        val selectionClipboard = ClipboardSelectionRenderer.sparseClipboard(layout.clipboardBuffer)
        val ghostClipboard = ClipboardSelectionRenderer.surfaceClipboard(selectionClipboard)
        val sparseDestination = ClipboardSelectionRenderer.isSparse(layout.region, selectionClipboard)
        val nonAirCells = ghostClipboard.nonAirCells()

        BlockPreviewPipeline.renderOverlay(
            context = context,
            scene = BlockPreviewPipeline.OverlayScene(
                origins = if (nonAirCells.isNotEmpty()) listOf(layout.region.minCorner()) else emptyList(),
                clipboard = selectionClipboard,
                fallbackClipboard = ghostClipboard,
                color = DESTINATION_GHOST_COLOR,
                alpha = if (sparseDestination) SPARSE_GHOST_ALPHA else DEFAULT_GHOST_ALPHA,
                scale = GHOST_SCALE,
                sessionTag = "smear-destination",
            ),
        )
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
        val ghostInstanceCount = repeatCount.coerceAtLeast(0).toLong() + if (includeSourceOrigin) 1L else 0L
        val renderGhost = isDestinationGhostWithinBudget(
            occupiedCellCount = selectionClipboard.nonAirCells().size,
            instanceCount = ghostInstanceCount,
        )
        val destinationGhostClipboard = if (renderGhost) {
            ClipboardSelectionRenderer.surfaceClipboard(selectionClipboard)
        } else {
            selectionClipboard
        }
        val sparseDestination = ClipboardSelectionRenderer.isSparse(sourceRegion, selectionClipboard)
        val ghostClipboard = destinationGhostClipboard
        val smearOffsets = if (mode == RegionRepeatPlacementService.Mode.SMEAR && renderGhost) {
            RegionRepeatPlacementService.smearOffsets(step, repeatCount)
        } else {
            emptyList()
        }
        val aggregateRegion = if (mode == RegionRepeatPlacementService.Mode.SMEAR) {
            val aggregateOffsets = if (renderGhost) {
                listOf(Vec3i.ZERO) + smearOffsets
            } else {
                listOf(Vec3i.ZERO, step)
            }
            aggregateRegionForOffsets(sourceRegion, aggregateOffsets)
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
                if (mode == RegionRepeatPlacementService.Mode.SMEAR && renderGhost) {
                    destinationRegionsForOffsets(sourceRegion, smearOffsets.take(MAX_REGION_OUTLINES))
                } else if (mode != RegionRepeatPlacementService.Mode.SMEAR) {
                    RepeatPreviewLayout.destinationRegions(
                        sourceRegion = sourceRegion,
                        step = step,
                        repeatCount = repeatCount,
                        maxRegions = MAX_REGION_OUTLINES,
                    )
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val nonAirCells = ghostClipboard.nonAirCells()
            if (nonAirCells.isNotEmpty()) {
                val maxLegacyGhostOrigins = if (renderGhost) {
                    maxOf(1, GhostBlockPreviewRenderer.maxOriginsFor(nonAirCells.size))
                } else {
                    0
                }
                val canRenderAllGhostOrigins = renderGhost && repeatCount <= maxLegacyGhostOrigins
                val baseGhostOrigins = when {
                    !renderGhost -> emptyList()
                    mode == RegionRepeatPlacementService.Mode.SMEAR ->
                        destinationRegionsForOffsets(sourceRegion, smearOffsets).map { it.minCorner() }
                    else -> RepeatPreviewLayout.destinationRegions(
                        sourceRegion = sourceRegion,
                        step = step,
                        repeatCount = repeatCount,
                        maxRegions = repeatCount,
                    ).map { it.minCorner() }
                }
                val ghostOrigins = if (!renderGhost) {
                    emptyList()
                } else if (includeSourceOrigin) {
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
                        renderGhost = renderGhost,
                        pulseSelection = mode == RegionRepeatPlacementService.Mode.STACK,
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
                        pulseSelection = mode == RegionRepeatPlacementService.Mode.STACK,
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
                        pulseSelection = mode == RegionRepeatPlacementService.Mode.STACK,
                    ),
                )
            }
        }
    }

    private fun isDestinationGhostWithinBudget(
        occupiedCellCount: Int,
        instanceCount: Long,
    ): Boolean {
        return occupiedCellCount.toLong() * instanceCount.coerceAtLeast(0L) <=
            MAX_DESTINATION_OCCUPANCY_CELLS
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

    private fun clippedSmearLayout(
        world: World,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        step: Vec3i,
        repeatCount: Int,
    ): ClippedSmearLayout? {
        val offsets = RegionRepeatPlacementService.smearOffsets(step, repeatCount)
        if (offsets.isEmpty()) {
            return null
        }

        val source = sourceRegion.normalized()
        val sourceOrigin = source.minCorner()
        val sourcePositions = clipboardBuffer.cells.mapTo(linkedSetOf()) { cell ->
            BlockPos(
                sourceOrigin.x + cell.offset.x,
                sourceOrigin.y + cell.offset.y,
                sourceOrigin.z + cell.offset.z,
            )
        }
        val candidates = linkedMapOf<BlockPos, ClippedSmearCandidate>()

        clipboardBuffer.cells.forEach { cell ->
            if (cell.state.isAir) {
                return@forEach
            }

            offsets.forEach { offset ->
                val destination = BlockPos(
                    sourceOrigin.x + cell.offset.x + offset.x,
                    sourceOrigin.y + cell.offset.y + offset.y,
                    sourceOrigin.z + cell.offset.z + offset.z,
                )
                if (destination !in sourcePositions && !world.getBlockState(destination).isAir) {
                    return@forEach
                }

                val distanceSq = offset.x * offset.x + offset.y * offset.y + offset.z * offset.z
                val existing = candidates[destination]
                if (existing == null || distanceSq < existing.distanceSq) {
                    candidates[destination] = ClippedSmearCandidate(
                        pos = destination,
                        cell = cell,
                        distanceSq = distanceSq,
                    )
                }
            }
        }

        if (candidates.isEmpty()) {
            return null
        }

        val region = boundingRegion(candidates.keys)
        val min = region.minCorner()
        val cells = candidates.values
            .sortedWith(compareBy<ClippedSmearCandidate> { it.pos.x }.thenBy { it.pos.y }.thenBy { it.pos.z })
            .map { candidate ->
                ClipboardCell(
                    offset = Vec3i(
                        candidate.pos.x - min.x,
                        candidate.pos.y - min.y,
                        candidate.pos.z - min.z,
                    ),
                    state = candidate.cell.state,
                    blockEntityData = candidate.cell.blockEntityData?.copy(),
                )
            }

        return ClippedSmearLayout(
            region = region,
            clipboardBuffer = ClipboardBuffer(size = region.size(), cells = cells),
        )
    }

    private fun boundingRegion(positions: Collection<BlockPos>): BlockRegion {
        val iterator = positions.iterator()
        val first = iterator.next()
        var minX = first.x
        var minY = first.y
        var minZ = first.z
        var maxX = first.x
        var maxY = first.y
        var maxZ = first.z
        while (iterator.hasNext()) {
            val pos = iterator.next()
            minX = minOf(minX, pos.x)
            minY = minOf(minY, pos.y)
            minZ = minOf(minZ, pos.z)
            maxX = maxOf(maxX, pos.x)
            maxY = maxOf(maxY, pos.y)
            maxZ = maxOf(maxZ, pos.z)
        }
        return BlockRegion(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ)).normalized()
    }

    private data class ClippedSmearLayout(
        val region: BlockRegion,
        val clipboardBuffer: ClipboardBuffer,
    )

    private data class ClippedSmearCandidate(
        val pos: BlockPos,
        val cell: ClipboardCell,
        val distanceSq: Int,
    )
}

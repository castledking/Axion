package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.operation.SmearRegionOperation
import axion.common.operation.StackRegionOperation
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i
import kotlin.math.abs

object RegionRepeatPlacementService {
    enum class Mode {
        STACK,
        SMEAR,
    }

    fun createInitialPreview(
        client: MinecraftClient,
        firstCorner: BlockPos,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        scrollAmount: Double,
        mode: Mode,
    ): RepeatRegionPreview? {
        val scrollDirection = scrollAmount.compareTo(0.0)
        if (scrollDirection == 0) {
            return null
        }

        val direction = dominantLookDirection(client)
        return createPreview(
            firstCorner = firstCorner,
            sourceRegion = sourceRegion,
            clipboardBuffer = clipboardBuffer,
            lookDirection = direction,
            step = stepFor(sourceRegion, direction, scrollDirection),
            scrollSign = scrollDirection,
            repeatCount = 1,
            committedSegments = emptyList(),
        )
    }

    fun nudgePreview(
        client: MinecraftClient,
        preview: RepeatRegionPreview,
        scrollAmount: Double,
        mode: Mode,
    ): RepeatRegionPreview? {
        val scrollDirection = scrollAmount.compareTo(0.0)
        if (scrollDirection == 0) {
            return preview
        }

        val currentDirection = dominantLookDirection(client)
        return if (preview.lookDirection == currentDirection) {
            nudgeCurrentSegment(preview, scrollDirection)
        } else {
            redirectPreview(preview, currentDirection, scrollDirection, mode)
        }
    }

    fun toOperation(preview: RepeatRegionPreview, mode: Mode): EditOperation {
        val currentOperation = toOperation(
            sourceRegion = preview.sourceRegion,
            clipboardBuffer = preview.clipboardBuffer,
            step = preview.step,
            repeatCount = preview.repeatCount,
            mode = mode,
        )
        val committedOperations = preview.committedSegments.map { segment ->
            toOperation(
                sourceRegion = segment.sourceRegion,
                clipboardBuffer = segment.clipboardBuffer,
                step = segment.step,
                repeatCount = segment.repeatCount,
                mode = mode,
            )
        }
        return when {
            committedOperations.isEmpty() -> currentOperation
            else -> CompositeOperation(committedOperations + currentOperation)
        }
    }

    private fun nudgeCurrentSegment(
        preview: RepeatRegionPreview,
        scrollDirection: Int,
    ): RepeatRegionPreview? {
        val currentSignedCount = preview.repeatCount * preview.scrollSign
        val nextSignedCount = currentSignedCount + scrollDirection
        if (nextSignedCount == 0) {
            return null
        }

        val nextScrollSign = intSign(nextSignedCount)
        val nextStep = if (nextScrollSign != preview.scrollSign) {
            preview.step.multiply(-1)
        } else {
            preview.step
        }

        return createPreview(
            firstCorner = preview.firstCorner,
            sourceRegion = preview.sourceRegion,
            clipboardBuffer = preview.clipboardBuffer,
            lookDirection = preview.lookDirection,
            step = nextStep,
            scrollSign = nextScrollSign,
            repeatCount = abs(nextSignedCount),
            committedSegments = preview.committedSegments,
        )
    }

    private fun redirectPreview(
        preview: RepeatRegionPreview,
        nextDirection: Direction,
        scrollDirection: Int,
        mode: Mode,
    ): RepeatRegionPreview {
        val folded = foldPreview(preview, mode)
        return createPreview(
            firstCorner = preview.firstCorner,
            sourceRegion = folded.region,
            clipboardBuffer = folded.clipboardBuffer,
            lookDirection = nextDirection,
            step = stepFor(folded.region, nextDirection, scrollDirection),
            scrollSign = scrollDirection,
            repeatCount = 1,
            committedSegments = preview.committedSegments + folded.segment,
        )
    }

    private fun createPreview(
        firstCorner: BlockPos,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        lookDirection: Direction,
        step: Vec3i,
        scrollSign: Int,
        repeatCount: Int,
        committedSegments: List<RepeatPreviewSegment>,
    ): RepeatRegionPreview {
        val normalized = sourceRegion.normalized()
        return RepeatRegionPreview(
            firstCorner = firstCorner,
            sourceRegion = normalized,
            clipboardBuffer = clipboardBuffer,
            lookDirection = lookDirection,
            step = step,
            scrollSign = scrollSign,
            repeatCount = repeatCount,
            committedSegments = committedSegments,
        )
    }

    private fun toOperation(
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        step: Vec3i,
        repeatCount: Int,
        mode: Mode,
    ): EditOperation {
        return when (mode) {
            Mode.STACK -> StackRegionOperation(
                sourceRegion = sourceRegion,
                clipboardBuffer = clipboardBuffer,
                step = step,
                repeatCount = repeatCount,
            )

            Mode.SMEAR -> SmearRegionOperation(
                sourceRegion = sourceRegion,
                clipboardBuffer = clipboardBuffer,
                step = step,
                repeatCount = repeatCount,
            )
        }
    }

    private fun foldPreview(
        preview: RepeatRegionPreview,
        mode: Mode,
    ): FoldedRepeatPreview {
        val sourceOrigin = preview.sourceRegion.minCorner()
        val absoluteCells = linkedMapOf<BlockPos, ClipboardCell>()

        preview.clipboardBuffer.cells.forEach { cell ->
            val absolutePos = sourceOrigin.add(cell.offset).toImmutable()
            absoluteCells[absolutePos] = cell.copy(offset = Vec3i(absolutePos.x, absolutePos.y, absolutePos.z))
        }

        for (index in 1..preview.repeatCount) {
            val destinationOrigin = sourceOrigin.add(preview.step.multiply(index))
            preview.clipboardBuffer.cells.forEach { cell ->
                val absolutePos = destinationOrigin.add(cell.offset).toImmutable()
                val existing = absoluteCells[absolutePos]
                if (mode == Mode.SMEAR && existing != null && !existing.state.isAir) {
                    return@forEach
                }
                absoluteCells[absolutePos] = cell.copy(offset = Vec3i(absolutePos.x, absolutePos.y, absolutePos.z))
            }
        }

        val region = boundingRegion(absoluteCells.keys)
        val min = region.minCorner()
        val max = region.maxCorner()
        val foldedCells = buildList {
            for (pos in BlockPos.iterate(min, max)) {
                val absolutePos = pos.toImmutable()
                val cell = absoluteCells[absolutePos]
                add(
                    ClipboardCell(
                        offset = Vec3i(
                            absolutePos.x - min.x,
                            absolutePos.y - min.y,
                            absolutePos.z - min.z,
                        ),
                        state = cell?.state ?: Blocks.AIR.defaultState,
                        blockEntityData = cell?.blockEntityData?.copy(),
                    ),
                )
            }
        }

        return FoldedRepeatPreview(
            region = region,
            clipboardBuffer = ClipboardBuffer(size = region.size(), cells = foldedCells),
            segment = RepeatPreviewSegment(
                sourceRegion = preview.sourceRegion,
                clipboardBuffer = preview.clipboardBuffer,
                step = preview.step,
                repeatCount = preview.repeatCount,
            ),
        )
    }

    private fun dominantLookDirection(client: MinecraftClient): Direction {
        val look = client.player?.rotationVecClient ?: return Direction.UP
        return Direction.getFacing(look)
    }

    private fun stepFor(region: BlockRegion, direction: Direction, scrollDirection: Int): Vec3i {
        val stepLength = region.normalized().size().componentAlong(direction.axis)
        return direction.vector.multiply(stepLength * scrollDirection)
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

    private fun Vec3i.componentAlong(axis: Direction.Axis): Int {
        return when (axis) {
            Direction.Axis.X -> x
            Direction.Axis.Y -> y
            Direction.Axis.Z -> z
        }
    }

    private fun intSign(value: Int): Int {
        return when {
            value > 0 -> 1
            value < 0 -> -1
            else -> 0
        }
    }

    private data class FoldedRepeatPreview(
        val region: BlockRegion,
        val clipboardBuffer: ClipboardBuffer,
        val segment: RepeatPreviewSegment,
    )
}

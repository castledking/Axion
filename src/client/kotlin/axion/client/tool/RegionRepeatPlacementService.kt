package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i
import kotlin.math.abs

object RegionRepeatPlacementService {
    fun createInitialPreview(
        client: MinecraftClient,
        firstCorner: BlockPos,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        scrollAmount: Double,
    ): RepeatRegionPreview? {
        val scrollDirection = scrollAmount.compareTo(0.0)
        if (scrollDirection == 0) {
            return null
        }

        val direction = dominantLookDirection(client)
        val stepLength = sourceRegion.normalized().size().componentAlong(direction.axis)
        val step = direction.vector.multiply(stepLength * scrollDirection)
        return createPreview(
            firstCorner = firstCorner,
            sourceRegion = sourceRegion,
            clipboardBuffer = clipboardBuffer,
            step = step,
            scrollSign = scrollDirection,
            repeatCount = 1,
        )
    }

    fun nudgePreview(preview: RepeatRegionPreview, scrollAmount: Double): RepeatRegionPreview? {
        val scrollDirection = scrollAmount.compareTo(0.0)
        if (scrollDirection == 0) {
            return preview
        }

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
            step = nextStep,
            scrollSign = nextScrollSign,
            repeatCount = abs(nextSignedCount),
        )
    }

    private fun createPreview(
        firstCorner: BlockPos,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        step: Vec3i,
        scrollSign: Int,
        repeatCount: Int,
    ): RepeatRegionPreview {
        val normalized = sourceRegion.normalized()
        val destinationRegions = (1..repeatCount).map { index ->
            normalized.offset(step.multiply(index)).normalized()
        }

        return RepeatRegionPreview(
            firstCorner = firstCorner,
            sourceRegion = normalized,
            clipboardBuffer = clipboardBuffer,
            step = step,
            scrollSign = scrollSign,
            repeatCount = repeatCount,
            destinationRegions = destinationRegions,
        )
    }

    private fun dominantLookDirection(client: MinecraftClient): Direction {
        val look = client.player?.rotationVecClient ?: return Direction.UP
        return Direction.getFacing(look)
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
}

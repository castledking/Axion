package axion.client.render

import axion.client.tool.RepeatPreviewSegment
import axion.common.model.BlockRegion
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

object RepeatPreviewLayout {

    fun globalAggregateRegion(
        segments: List<RepeatPreviewSegment>,
        activeSourceRegion: BlockRegion,
        activeStep: Vec3i,
        activeRepeatCount: Int,
    ): BlockRegion? {
        val regions = mutableListOf<BlockRegion>()
        segments.forEach { seg ->
            // Always calculate from sourceRegion, step, and repeatCount
            // This ensures the aggregate region is based on where blocks are actually placed
            aggregateRegion(seg.sourceRegion, seg.step, 0, seg.repeatCount)?.let { regions += it }
        }
        aggregateRegion(activeSourceRegion, activeStep, 0, activeRepeatCount)?.let { regions += it }
        if (regions.isEmpty()) return null
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
        return BlockRegion(min, max)
    }
    fun destinationRegions(
        sourceRegion: BlockRegion,
        step: Vec3i,
        repeatCount: Int,
        maxRegions: Int = repeatCount,
    ): List<BlockRegion> {
        if (repeatCount <= 0 || maxRegions <= 0) {
            return emptyList()
        }

        val normalized = sourceRegion.normalized()
        return (1..minOf(repeatCount, maxRegions)).map { index ->
            normalized.offset(step.multiply(index)).normalized()
        }
    }

    fun aggregateRegion(
        sourceRegion: BlockRegion,
        step: Vec3i,
        startIndex: Int,
        endIndex: Int,
    ): BlockRegion? {
        if (startIndex > endIndex) {
            return null
        }

        val first = sourceRegion.normalized().offset(step.multiply(startIndex)).normalized()
        val last = sourceRegion.normalized().offset(step.multiply(endIndex)).normalized()
        val min = BlockPos(
            minOf(first.minCorner().x, last.minCorner().x),
            minOf(first.minCorner().y, last.minCorner().y),
            minOf(first.minCorner().z, last.minCorner().z),
        )
        val max = BlockPos(
            maxOf(first.maxCorner().x, last.maxCorner().x),
            maxOf(first.maxCorner().y, last.maxCorner().y),
            maxOf(first.maxCorner().z, last.maxCorner().z),
        )
        return BlockRegion(min, max).normalized()
    }
}

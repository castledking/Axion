package axion.common.model

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3i

data class BlockRegion(
    val start: BlockPos,
    val end: BlockPos,
) {
    fun normalized(): BlockRegion {
        val minX = minOf(start.x, end.x)
        val minY = minOf(start.y, end.y)
        val minZ = minOf(start.z, end.z)
        val maxX = maxOf(start.x, end.x)
        val maxY = maxOf(start.y, end.y)
        val maxZ = maxOf(start.z, end.z)

        return BlockRegion(
            start = BlockPos(minX, minY, minZ),
            end = BlockPos(maxX, maxY, maxZ),
        )
    }

    fun minCorner(): BlockPos = normalized().start

    fun maxCorner(): BlockPos = normalized().end

    fun contains(pos: BlockPos): Boolean {
        val normalized = normalized()
        return pos.x in normalized.start.x..normalized.end.x &&
            pos.y in normalized.start.y..normalized.end.y &&
            pos.z in normalized.start.z..normalized.end.z
    }

    fun expandFace(face: RegionFace, target: BlockPos): BlockRegion {
        val normalized = normalized()
        val min = normalized.start
        val max = normalized.end

        return when (face) {
            RegionFace.DOWN -> BlockRegion(BlockPos(min.x, minOf(target.y, max.y), min.z), max)
            RegionFace.UP -> BlockRegion(min, BlockPos(max.x, maxOf(target.y, min.y), max.z))
            RegionFace.NORTH -> BlockRegion(BlockPos(min.x, min.y, minOf(target.z, max.z)), max)
            RegionFace.SOUTH -> BlockRegion(min, BlockPos(max.x, max.y, maxOf(target.z, min.z)))
            RegionFace.WEST -> BlockRegion(BlockPos(minOf(target.x, max.x), min.y, min.z), max)
            RegionFace.EAST -> BlockRegion(min, BlockPos(maxOf(target.x, min.x), max.y, max.z))
        }.normalized()
    }

    fun size(): Vec3i {
        val normalized = normalized()
        return Vec3i(
            normalized.end.x - normalized.start.x + 1,
            normalized.end.y - normalized.start.y + 1,
            normalized.end.z - normalized.start.z + 1,
        )
    }

    fun offset(delta: Vec3i): BlockRegion {
        return BlockRegion(start.add(delta), end.add(delta))
    }

    fun oppositeCorner(anchor: BlockPos): BlockPos {
        val normalized = normalized()
        return BlockPos(
            if (anchor.x == normalized.start.x) normalized.end.x else normalized.start.x,
            if (anchor.y == normalized.start.y) normalized.end.y else normalized.start.y,
            if (anchor.z == normalized.start.z) normalized.end.z else normalized.start.z,
        )
    }

    fun toBox(): Box {
        val normalized = normalized()
        return Box(
            normalized.start.x.toDouble(),
            normalized.start.y.toDouble(),
            normalized.start.z.toDouble(),
            normalized.end.x + 1.0,
            normalized.end.y + 1.0,
            normalized.end.z + 1.0,
        )
    }
}

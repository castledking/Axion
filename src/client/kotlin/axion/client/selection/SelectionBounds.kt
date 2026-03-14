package axion.client.selection

import axion.common.model.BlockRegion
import axion.common.model.RegionFace
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

object SelectionBounds {
    private const val OUTLINE_PADDING: Double = 0.002
    private const val FACE_THICKNESS: Double = 0.045

    fun blockBox(pos: BlockPos): Box {
        return Box(
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            pos.x + 1.0,
            pos.y + 1.0,
            pos.z + 1.0,
        )
    }

    fun regionBox(region: BlockRegion): Box = region.normalized().toBox()

    fun outlineBox(box: Box): Box = box.expand(OUTLINE_PADDING)

    fun faceBox(pos: BlockPos, face: RegionFace): Box {
        val box = blockBox(pos)
        return when (face) {
            RegionFace.DOWN -> Box(box.minX, box.minY, box.minZ, box.maxX, box.minY + FACE_THICKNESS, box.maxZ)
            RegionFace.UP -> Box(box.minX, box.maxY - FACE_THICKNESS, box.minZ, box.maxX, box.maxY, box.maxZ)
            RegionFace.NORTH -> Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ + FACE_THICKNESS)
            RegionFace.SOUTH -> Box(box.minX, box.minY, box.maxZ - FACE_THICKNESS, box.maxX, box.maxY, box.maxZ)
            RegionFace.WEST -> Box(box.minX, box.minY, box.minZ, box.minX + FACE_THICKNESS, box.maxY, box.maxZ)
            RegionFace.EAST -> Box(box.maxX - FACE_THICKNESS, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
        }
    }

    fun pickFace(region: BlockRegion, point: Vec3d): RegionFace {
        val box = regionBox(region)
        val distances = listOf(
            RegionFace.WEST to kotlin.math.abs(point.x - box.minX),
            RegionFace.EAST to kotlin.math.abs(point.x - box.maxX),
            RegionFace.DOWN to kotlin.math.abs(point.y - box.minY),
            RegionFace.UP to kotlin.math.abs(point.y - box.maxY),
            RegionFace.NORTH to kotlin.math.abs(point.z - box.minZ),
            RegionFace.SOUTH to kotlin.math.abs(point.z - box.maxZ),
        )

        return distances.minBy { it.second }.first
    }
}

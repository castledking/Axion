package axion.client.current

import axion.common.model.BlockRegion
import axion.common.model.RegionFace
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object SelectionBounds {
    private const val OUTLINE_PADDING: Double = 0.002
    private const val FACE_THICKNESS: Double = 0.045
    private const val RAY_EPSILON: Double = 1.0e-7

    fun blockBox(pos: BlockPos): AABB {
        return AABB(
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            pos.x + 1.0,
            pos.y + 1.0,
            pos.z + 1.0,
        )
    }

    fun regionBox(region: BlockRegion): AABB = region.normalized().toBox()

    fun outlineBox(box: AABB): AABB = box.expand(OUTLINE_PADDING, OUTLINE_PADDING, OUTLINE_PADDING)

    fun faceBox(pos: BlockPos, face: RegionFace): AABB {
        val box = blockBox(pos)
        return when (face) {
            RegionFace.DOWN -> AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + FACE_THICKNESS, box.maxZ)
            RegionFace.UP -> AABB(box.minX, box.maxY - FACE_THICKNESS, box.minZ, box.maxX, box.maxY, box.maxZ)
            RegionFace.NORTH -> AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ + FACE_THICKNESS)
            RegionFace.SOUTH -> AABB(box.minX, box.minY, box.maxZ - FACE_THICKNESS, box.maxX, box.maxY, box.maxZ)
            RegionFace.WEST -> AABB(box.minX, box.minY, box.minZ, box.minX + FACE_THICKNESS, box.maxY, box.maxZ)
            RegionFace.EAST -> AABB(box.maxX - FACE_THICKNESS, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
        }
    }

    fun pickFace(region: BlockRegion, point: Vec3): RegionFace {
        val box = regionBox(region)
        val distances = listOf(
            RegionFace.WEST to abs(point.x - box.minX),
            RegionFace.EAST to abs(point.x - box.maxX),
            RegionFace.DOWN to abs(point.y - box.minY),
            RegionFace.UP to abs(point.y - box.maxY),
            RegionFace.NORTH to abs(point.z - box.minZ),
            RegionFace.SOUTH to abs(point.z - box.maxZ),
        )

        return distances.minBy { it.second }.first
    }

    fun outwardFaceToward(region: BlockRegion, target: BlockPos, point: Vec3): RegionFace? {
        val normalized = region.normalized()
        val box = regionBox(normalized)
        val center = Vec3(target.x + 0.5, target.y + 0.5, target.z + 0.5)
        val candidates = buildList {
            if (target.x < normalized.start.x) {
                add(
                    Candidate(
                        face = RegionFace.WEST,
                        centerDistance = abs(center.x - box.minX),
                        hitDistance = abs(point.x - box.minX),
                        horizontal = true,
                    ),
                )
            }
            if (target.x > normalized.end.x) {
                add(
                    Candidate(
                        face = RegionFace.EAST,
                        centerDistance = abs(center.x - box.maxX),
                        hitDistance = abs(point.x - box.maxX),
                        horizontal = true,
                    ),
                )
            }
            if (target.y < normalized.start.y) {
                add(
                    Candidate(
                        face = RegionFace.DOWN,
                        centerDistance = abs(center.y - box.minY),
                        hitDistance = abs(point.y - box.minY),
                        horizontal = false,
                    ),
                )
            }
            if (target.y > normalized.end.y) {
                add(
                    Candidate(
                        face = RegionFace.UP,
                        centerDistance = abs(center.y - box.maxY),
                        hitDistance = abs(point.y - box.maxY),
                        horizontal = false,
                    ),
                )
            }
            if (target.z < normalized.start.z) {
                add(
                    Candidate(
                        face = RegionFace.NORTH,
                        centerDistance = abs(center.z - box.minZ),
                        hitDistance = abs(point.z - box.minZ),
                        horizontal = true,
                    ),
                )
            }
            if (target.z > normalized.end.z) {
                add(
                    Candidate(
                        face = RegionFace.SOUTH,
                        centerDistance = abs(center.z - box.maxZ),
                        hitDistance = abs(point.z - box.maxZ),
                        horizontal = true,
                    ),
                )
            }
        }

        return candidates.minWithOrNull(
            compareBy<Candidate> { it.centerDistance }
                .thenByDescending { it.horizontal }
                .thenBy { it.hitDistance },
        )?.face
    }

    fun raycastFace(region: BlockRegion, origin: Vec3, direction: Vec3, maxDistance: Double): FaceHit? {
        val box = regionBox(region)
        var tMin = 0.0
        var tMax = maxDistance
        var entryFace: RegionFace? = null
        var exitFace: RegionFace? = null

        data class AxisStep(
            val origin: Double,
            val direction: Double,
            val min: Double,
            val max: Double,
            val negativeFace: RegionFace,
            val positiveFace: RegionFace,
        )

        val axes = listOf(
            AxisStep(origin.x, direction.x, box.minX, box.maxX, RegionFace.WEST, RegionFace.EAST),
            AxisStep(origin.y, direction.y, box.minY, box.maxY, RegionFace.DOWN, RegionFace.UP),
            AxisStep(origin.z, direction.z, box.minZ, box.maxZ, RegionFace.NORTH, RegionFace.SOUTH),
        )

        axes.forEach { axis ->
            if (abs(axis.direction) < RAY_EPSILON) {
                if (axis.origin < axis.min || axis.origin > axis.max) {
                    return null
                }
                return@forEach
            }

            var nearT = (axis.min - axis.origin) / axis.direction
            var farT = (axis.max - axis.origin) / axis.direction
            var nearFace = axis.negativeFace
            var farFace = axis.positiveFace
            if (nearT > farT) {
                val swapT = nearT
                nearT = farT
                farT = swapT
                val swapFace = nearFace
                nearFace = farFace
                farFace = swapFace
            }

            if (nearT > tMin) {
                tMin = nearT
                entryFace = nearFace
            }
            if (farT < tMax) {
                tMax = farT
                exitFace = farFace
            }
            if (tMin - tMax > RAY_EPSILON) {
                return null
            }
        }

        val inside = contains(region, origin)
        val t = if (inside) tMax else tMin
        val face = if (inside) exitFace else entryFace
        if (face == null || t < 0.0 || t > maxDistance) {
            return null
        }

        return FaceHit(
            face = face,
            point = origin.add(direction.x * t, direction.y * t, direction.z * t),
        )
    }

    private fun contains(region: BlockRegion, point: Vec3): Boolean {
        val box = regionBox(region)
        return point.x in box.minX..box.maxX &&
            point.y in box.minY..box.maxY &&
            point.z in box.minZ..box.maxZ
    }

    data class FaceHit(
        val face: RegionFace,
        val point: Vec3,
    )

    private data class Candidate(
        val face: RegionFace,
        val centerDistance: Double,
        val hitDistance: Double,
        val horizontal: Boolean,
    )
}

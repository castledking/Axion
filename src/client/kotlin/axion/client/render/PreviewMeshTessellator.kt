package axion.client.render

import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i
import java.util.LinkedHashMap
import kotlin.math.abs

object PreviewMeshTessellator {
    private data class ShellTemplate(
        val occupiedCells: List<ClipboardCell>,
        val exposedFaces: List<ExposedFace>,
    )

    private data class ExposedFace(
        val offset: Vec3i,
        val state: BlockState,
        val face: Direction,
    )

    private data class ResolvedFace(
        val state: BlockState,
        val face: Direction,
        val bounds: Box,
        val blockPos: BlockPos,
    )

    private data class FaceMergeKey(
        val state: BlockState,
        val face: Direction,
        val plane: Int,
    )

    private data class MergeCell(
        val state: BlockState,
        val face: Direction,
        val gridA: Int,
        val gridB: Int,
        val plane: Int,
    )

    private val shellTemplateCache = object : LinkedHashMap<ClipboardBuffer, ShellTemplate>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ClipboardBuffer, ShellTemplate>?) = size > 16
    }

    fun buildShellMesh(
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        maxQuads: Int,
    ): PreviewMesh {
        if (origins.isEmpty() || maxQuads <= 0) {
            return PreviewMesh(emptyList())
        }

        val template = shellTemplate(clipboard)
        if (template.exposedFaces.isEmpty()) {
            return PreviewMesh(emptyList())
        }

        val maxOrigins = maxOf(1, GhostBlockPreviewRenderer.maxOriginsFor(template.occupiedCells.size))
        val boundedOrigins = origins.asSequence().take(maxOrigins).toList()
        if (boundedOrigins.isEmpty()) {
            return PreviewMesh(emptyList())
        }

        val resolvedFaces = ArrayList<ResolvedFace>(template.exposedFaces.size * boundedOrigins.size)
        boundedOrigins.forEach { origin ->
            template.exposedFaces.forEach { exposedFace ->
                val blockPos = origin.add(exposedFace.offset)
                val bounds = Box(
                    blockPos.x.toDouble(),
                    blockPos.y.toDouble(),
                    blockPos.z.toDouble(),
                    blockPos.x + 1.0,
                    blockPos.y + 1.0,
                    blockPos.z + 1.0,
                )
                resolvedFaces += ResolvedFace(
                    state = exposedFace.state,
                    face = exposedFace.face,
                    bounds = bounds,
                    blockPos = blockPos,
                )
            }
        }

        val (mergeable, singleFaces) = resolvedFaces.partition { isFullCube(it.bounds, it.blockPos) }
        val mergedFaces = mergeFaces(mergeable)
        val quads = ArrayList<PreviewQuad>(maxQuads)

        mergedFaces.asSequence().take(maxQuads).forEach { quad ->
            quads += quad
        }
        if (quads.size < maxQuads) {
            singleFaces.asSequence().take(maxQuads - quads.size).forEach { resolved ->
                quads += PreviewQuad(
                    state = resolved.state,
                    face = resolved.face,
                    bounds = resolved.bounds,
                )
            }
        }

        return PreviewMesh(quads)
    }

    private fun shellTemplate(source: ClipboardBuffer): ShellTemplate {
        return shellTemplateCache.getOrPut(source) {
            val fullClipboard = ClipboardSelectionRenderer.sparseClipboard(source)
            val occupiedCells = fullClipboard.nonAirCells()
            val statesByOffset = HashMap<Long, BlockState>(occupiedCells.size)
            occupiedCells.forEach { cell ->
                statesByOffset[BlockPos.asLong(cell.offset.x, cell.offset.y, cell.offset.z)] = cell.state
            }

            val exposedFaces = ArrayList<ExposedFace>(occupiedCells.size * 2)
            occupiedCells.forEach { cell ->
                val state = cell.state
                if (state.isAir || state.renderType != BlockRenderType.MODEL) {
                    return@forEach
                }
                Direction.entries.forEach { face ->
                    val neighborKey = BlockPos.asLong(
                        cell.offset.x + face.offsetX,
                        cell.offset.y + face.offsetY,
                        cell.offset.z + face.offsetZ,
                    )
                    val neighbor = statesByOffset[neighborKey]
                    if (neighbor == null || neighbor.isAir) {
                        exposedFaces += ExposedFace(
                            offset = cell.offset,
                            state = state,
                            face = face,
                        )
                    }
                }
            }

            ShellTemplate(
                occupiedCells = occupiedCells,
                exposedFaces = exposedFaces,
            )
        }
    }

    private fun mergeFaces(
        faces: List<ResolvedFace>,
    ): List<PreviewQuad> {
        if (faces.isEmpty()) {
            return emptyList()
        }

        val grouped = LinkedHashMap<FaceMergeKey, MutableMap<Pair<Int, Int>, MergeCell>>()
        faces.forEach { face ->
            val key = FaceMergeKey(
                state = face.state,
                face = face.face,
                plane = facePlane(face.blockPos, face.face),
            )
            val coords = faceGridCoords(face.blockPos, face.face)
            grouped.getOrPut(key) { LinkedHashMap() }[coords] = MergeCell(
                state = face.state,
                face = face.face,
                gridA = coords.first,
                gridB = coords.second,
                plane = key.plane,
            )
        }

        val result = ArrayList<PreviewQuad>(grouped.size)
        grouped.forEach { (key, cells) ->
            val visited = HashSet<Pair<Int, Int>>(cells.size)
            cells.keys.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second }).forEach { start ->
                if (!visited.add(start)) {
                    return@forEach
                }

                var width = 1
                while (cells.containsKey((start.first + width) to start.second) && !visited.contains((start.first + width) to start.second)) {
                    width++
                }

                var height = 1
                while (true) {
                    val nextRow = start.second + height
                    val fullRow = (0 until width).all { dx ->
                        val coord = (start.first + dx) to nextRow
                        cells.containsKey(coord) && !visited.contains(coord)
                    }
                    if (!fullRow) {
                        break
                    }
                    height++
                }

                for (dy in 0 until height) {
                    for (dx in 0 until width) {
                        visited += ((start.first + dx) to (start.second + dy))
                    }
                }

                result += PreviewQuad(
                    state = key.state,
                    face = key.face,
                    bounds = faceBounds(
                        face = key.face,
                        minA = start.first,
                        minB = start.second,
                        maxA = start.first + width,
                        maxB = start.second + height,
                        plane = key.plane,
                    ),
                )
            }
        }

        return result
    }

    private fun faceGridCoords(
        blockPos: BlockPos,
        face: Direction,
    ): Pair<Int, Int> {
        return when (face.axis) {
            Direction.Axis.X -> blockPos.z to blockPos.y
            Direction.Axis.Y -> blockPos.x to blockPos.z
            Direction.Axis.Z -> blockPos.x to blockPos.y
        }
    }

    private fun facePlane(
        blockPos: BlockPos,
        face: Direction,
    ): Int {
        return when (face) {
            Direction.WEST -> blockPos.x
            Direction.EAST -> blockPos.x + 1
            Direction.DOWN -> blockPos.y
            Direction.UP -> blockPos.y + 1
            Direction.NORTH -> blockPos.z
            Direction.SOUTH -> blockPos.z + 1
        }
    }

    private fun faceBounds(
        face: Direction,
        minA: Int,
        minB: Int,
        maxA: Int,
        maxB: Int,
        plane: Int,
    ): Box {
        return when (face) {
            Direction.WEST,
            Direction.EAST,
                -> Box(
                    plane.toDouble(),
                    minB.toDouble(),
                    minA.toDouble(),
                    plane.toDouble(),
                    maxB.toDouble(),
                    maxA.toDouble(),
                )

            Direction.DOWN,
            Direction.UP,
                -> Box(
                    minA.toDouble(),
                    plane.toDouble(),
                    minB.toDouble(),
                    maxA.toDouble(),
                    plane.toDouble(),
                    maxB.toDouble(),
                )

            Direction.NORTH,
            Direction.SOUTH,
                -> Box(
                    minA.toDouble(),
                    minB.toDouble(),
                    plane.toDouble(),
                    maxA.toDouble(),
                    maxB.toDouble(),
                    plane.toDouble(),
                )
        }
    }

    private fun isFullCube(
        box: Box,
        blockPos: BlockPos,
    ): Boolean {
        return abs(box.minX - blockPos.x.toDouble()) <= 0.0001 &&
            abs(box.minY - blockPos.y.toDouble()) <= 0.0001 &&
            abs(box.minZ - blockPos.z.toDouble()) <= 0.0001 &&
            abs(box.maxX - (blockPos.x + 1.0)) <= 0.0001 &&
            abs(box.maxY - (blockPos.y + 1.0)) <= 0.0001 &&
            abs(box.maxZ - (blockPos.z + 1.0)) <= 0.0001
    }
}

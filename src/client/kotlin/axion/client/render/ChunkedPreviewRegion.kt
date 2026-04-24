package axion.client.render

import axion.client.selection.SelectionBounds
import axion.common.model.ClipboardBuffer
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import java.util.LinkedHashMap

data class ChunkedPreviewRegion(
    val chunks: Map<Long, ChunkData>,
    val statesByPosition: Map<Long, BlockState>,
    val surfaceBlocks: List<PreviewBlock>,
) {
    data class ChunkData(
        val quads: List<PreviewQuad>,
        val outlineShape: VoxelShape,
    )

    data class PreviewBlock(
        val pos: BlockPos,
        val state: BlockState,
    )

    companion object {
        private const val MAX_CACHE_SIZE = 24

        private data class CacheKey(
            val clipboard: ClipboardBuffer,
            val origins: List<Long>,
            val maxQuads: Int,
        )

        private val cache = object : LinkedHashMap<CacheKey, ChunkedPreviewRegion>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, ChunkedPreviewRegion>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }

        fun getOrBuild(
            clipboard: ClipboardBuffer,
            origins: Collection<BlockPos>,
            maxQuads: Int,
        ): ChunkedPreviewRegion {
            val originKeys = origins.asSequence().map { it.asLong() }.sorted().toList()
            val key = CacheKey(
                clipboard = clipboard,
                origins = originKeys,
                maxQuads = maxQuads,
            )
            return synchronized(cache) {
                cache.getOrPut(key) {
                    build(clipboard, originKeys, maxQuads)
                }
            }
        }

        private fun build(
            clipboard: ClipboardBuffer,
            originKeys: List<Long>,
            maxQuads: Int,
        ): ChunkedPreviewRegion {
            val origins = originKeys.map(BlockPos::fromLong)
            val mesh = PreviewMeshTessellator.buildShellMesh(
                clipboard = clipboard,
                origins = origins,
                maxQuads = maxQuads,
            )
            val chunkQuads = LinkedHashMap<Long, MutableList<PreviewQuad>>()
            mesh.quads.forEach { quad ->
                val minChunkX = kotlin.math.floor(quad.bounds.minX).toInt() shr 4
                val minChunkZ = kotlin.math.floor(quad.bounds.minZ).toInt() shr 4
                val chunkKey = BlockPos.asLong(minChunkX, 0, minChunkZ)
                chunkQuads.getOrPut(chunkKey) { ArrayList() } += quad
            }

            val MAX_CHUNK_VOXEL_CELLS = 64
            val chunkShapes = LinkedHashMap<Long, VoxelShape>()
            val chunkCellCounts = LinkedHashMap<Long, Int>()
            val chunkBounds = LinkedHashMap<Long, DoubleArray>() // [minX, minY, minZ, maxX, maxY, maxZ]
            val statesByPosition = LinkedHashMap<Long, BlockState>()
            val surfaceBlocks = ArrayList<PreviewBlock>()
            val surfaceClipboard = ClipboardSelectionRenderer.surfaceClipboard(clipboard)
            origins.forEach { origin ->
                clipboard.nonAirCells().forEach { cell ->
                    val pos = origin.add(cell.offset)
                    statesByPosition[pos.asLong()] = cell.state
                }
                surfaceClipboard.nonAirCells().forEach { cell ->
                    val pos = origin.add(cell.offset)
                    val chunkKey = BlockPos.asLong(pos.x shr 4, 0, pos.z shr 4)
                    val count = (chunkCellCounts[chunkKey] ?: 0) + 1
                    chunkCellCounts[chunkKey] = count
                    val box = SelectionBounds.blockBox(pos)
                    if (count <= MAX_CHUNK_VOXEL_CELLS) {
                        val current = chunkShapes[chunkKey] ?: VoxelShapes.empty()
                        chunkShapes[chunkKey] = VoxelShapes.union(current, VoxelShapes.cuboid(box))
                    } else {
                        // Beyond threshold: accumulate bounding box instead
                        val bounds = chunkBounds.getOrPut(chunkKey) {
                            doubleArrayOf(
                                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                                Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE,
                            )
                        }
                        if (box.minX < bounds[0]) bounds[0] = box.minX
                        if (box.minY < bounds[1]) bounds[1] = box.minY
                        if (box.minZ < bounds[2]) bounds[2] = box.minZ
                        if (box.maxX > bounds[3]) bounds[3] = box.maxX
                        if (box.maxY > bounds[4]) bounds[4] = box.maxY
                        if (box.maxZ > bounds[5]) bounds[5] = box.maxZ
                    }
                    surfaceBlocks += PreviewBlock(
                        pos = pos,
                        state = cell.state,
                    )
                }
            }
            // For chunks that exceeded the threshold, replace their VoxelShape with a bounding box
            chunkBounds.forEach { (chunkKey, bounds) ->
                val existingShape = chunkShapes[chunkKey] ?: VoxelShapes.empty()
                val boundingBox = VoxelShapes.cuboid(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5])
                chunkShapes[chunkKey] = VoxelShapes.union(existingShape, boundingBox)
            }

            val chunks = LinkedHashMap<Long, ChunkData>()
            (chunkQuads.keys + chunkShapes.keys).forEach { chunkKey ->
                chunks[chunkKey] = ChunkData(
                    quads = chunkQuads[chunkKey] ?: emptyList(),
                    outlineShape = chunkShapes[chunkKey] ?: VoxelShapes.empty(),
                )
            }
            return ChunkedPreviewRegion(
                chunks = chunks,
                statesByPosition = statesByPosition,
                surfaceBlocks = surfaceBlocks,
            )
        }
    }
}

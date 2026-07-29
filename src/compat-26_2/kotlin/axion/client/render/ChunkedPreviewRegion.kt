package axion.client.render

import axion.client.render.gpu.PreviewStateHalo
import axion.client.selection.SelectionBounds
import axion.common.model.ClipboardBuffer
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import axion.client.compat.add
import axion.client.compat.blockPosFromLong
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

    fun clear() = Unit

    companion object {
        private const val MAX_CACHE_SIZE = 24

        private data class CacheKey(
            val clipboard: ClipboardBuffer,
            val surfaceClipboard: ClipboardBuffer,
            val origins: List<Long>,
            val maxQuads: Int,
        )

        private val cache = object : LinkedHashMap<CacheKey, ChunkedPreviewRegion>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, ChunkedPreviewRegion>?) = size > MAX_CACHE_SIZE
        }

        fun getOrBuild(
            clipboard: ClipboardBuffer,
            surfaceClipboard: ClipboardBuffer = ClipboardSelectionRenderer.surfaceClipboard(clipboard),
            origins: Collection<BlockPos>,
            maxQuads: Int,
        ): ChunkedPreviewRegion {
            val originKeys = origins.asSequence().map { it.asLong() }.sorted().toList()
            val key = CacheKey(
                clipboard = clipboard,
                surfaceClipboard = surfaceClipboard,
                origins = originKeys,
                maxQuads = maxQuads,
            )
            return synchronized(cache) {
                cache.getOrPut(key) {
                    build(clipboard, surfaceClipboard, originKeys, maxQuads)
                }
            }
        }

        private fun build(
            clipboard: ClipboardBuffer,
            surfaceClipboard: ClipboardBuffer,
            originKeys: List<Long>,
            maxQuads: Int,
        ): ChunkedPreviewRegion {
            val origins = originKeys.map { blockPosFromLong(it) }
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

            val chunkShapes = LinkedHashMap<Long, VoxelShape>()
            val statesByPosition = LinkedHashMap<Long, BlockState>()
            val surfaceBlocks = ArrayList<PreviewBlock>()
            val surfaceCells = surfaceClipboard.nonAirCells()
            val stateHalo = PreviewStateHalo.retain(clipboard.nonAirCells(), surfaceCells)
            origins.forEach { origin ->
                stateHalo.forEach { cell ->
                    val pos = origin.add(cell.offset)
                    statesByPosition[pos.asLong()] = cell.state
                }
                surfaceCells.forEach { cell ->
                    val pos = origin.add(cell.offset)
                    statesByPosition[pos.asLong()] = cell.state
                    val chunkKey = BlockPos.asLong(pos.x shr 4, 0, pos.z shr 4)
                    val current = chunkShapes[chunkKey] ?: VoxelShapes.empty()
                    chunkShapes[chunkKey] = VoxelShapes.union(
                        current,
                        VoxelShapes.cuboid(SelectionBounds.blockBox(pos)),
                    )
                    surfaceBlocks += PreviewBlock(
                        pos = pos,
                        state = cell.state,
                    )
                }
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

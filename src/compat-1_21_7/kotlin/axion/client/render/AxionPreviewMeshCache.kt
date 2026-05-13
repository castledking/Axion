package axion.client.render

import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.util.LinkedHashMap

/**
 * Caches tessellated preview mesh data so blocks are only re-tessellated when
 * the preview state actually changes (clipboard content, origins, color/alpha).
 *
 * Instead of calling renderBlockAsEntity() per block per frame, we:
 * 1. Build vertex data once via AxionBlockTessellator when the cache misses
 * 2. Store the built vertex data keyed by preview parameters
 * 3. Re-emit from cache every frame without re-tessellating
 *
 * This is the Axion equivalent of Axiom's ChunkRenderOverrider compilation step.
 */
object AxionPreviewMeshCache {
    private const val MAX_CACHE_SIZE = 32

    private data class CacheKey(
        val clipboard: ClipboardBuffer,
        val originKeys: List<Long>,
    )

    data class CachedMesh(
        val blocks: List<PreviewBlockInfo>,
        val statesByPosition: Map<Long, BlockState>,
    )

    private val cache = object : LinkedHashMap<CacheKey, CachedMesh>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, CachedMesh>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    fun getOrBuild(
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int,
        alpha: Int,
        scale: Float,
        maxBlocks: Int = 1536,
    ): CachedMesh? {
        val occupiedCells = clipboard.nonAirCells()
        if (occupiedCells.isEmpty() || origins.isEmpty()) {
            return null
        }

        val maxOrigins = maxOf(1, maxBlocks / occupiedCells.size.coerceAtLeast(1))
        val boundedOrigins = origins.asSequence().take(maxOrigins).toList()
        if (boundedOrigins.isEmpty()) {
            return null
        }

        val originKeys = boundedOrigins.map { it.asLong() }.sorted()
        val key = CacheKey(
            clipboard = clipboard,
            originKeys = originKeys,
        )

        return synchronized(cache) {
            cache.getOrPut(key) {
                buildMesh(occupiedCells, boundedOrigins, maxBlocks)
            }
        }
    }

    fun getOrBuildForWrites(
        writes: List<PreviewBlockInfo>,
        color: Int,
        alpha: Int,
    ): CachedMesh {
        val statesByPosition = LinkedHashMap<Long, BlockState>(writes.size)
        writes.forEach { block ->
            statesByPosition[block.pos.asLong()] = block.state
        }
        return CachedMesh(blocks = writes, statesByPosition = statesByPosition)
    }

    fun invalidate() {
        synchronized(cache) {
            cache.clear()
        }
    }

    fun invalidateForClipboard(clipboard: ClipboardBuffer) {
        synchronized(cache) {
            val keysToRemove = cache.keys.filter { it.clipboard == clipboard }
            keysToRemove.forEach { cache.remove(it) }
        }
    }

    private fun buildMesh(
        occupiedCells: List<ClipboardCell>,
        origins: List<BlockPos>,
        maxBlocks: Int,
    ): CachedMesh {
        val surfaceCells = filterSurfaceCells(occupiedCells)
        val cellsToRender = if (surfaceCells.size * origins.size <= maxBlocks) {
            surfaceCells
        } else {
            downsampleCells(surfaceCells, maxOf(1, maxBlocks / origins.size.coerceAtLeast(1)))
        }

        val blocks = ArrayList<PreviewBlockInfo>(cellsToRender.size * origins.size)
        val statesByPosition = LinkedHashMap<Long, BlockState>(occupiedCells.size * origins.size)

        origins.forEach { origin ->
            occupiedCells.forEach { cell ->
                val pos = cell.absolutePos(origin)
                statesByPosition[pos.asLong()] = cell.state
            }
        }

        origins.forEach { origin ->
            cellsToRender.forEach { cell ->
                val pos = cell.absolutePos(origin)
                if (cell.state.renderType == BlockRenderType.MODEL) {
                    blocks += PreviewBlockInfo(pos = pos, state = cell.state)
                }
            }
        }

        return CachedMesh(blocks = blocks, statesByPosition = statesByPosition)
    }

    private fun filterSurfaceCells(cells: List<ClipboardCell>): List<ClipboardCell> {
        val positionSet = HashSet<Long>(cells.size)
        cells.forEach { cell ->
            positionSet.add(BlockPos.asLong(cell.offset.x, cell.offset.y, cell.offset.z))
        }

        return cells.filter { cell ->
            if (cell.state.isAir) return@filter false
            Direction.entries.any { face ->
                val neighborKey = BlockPos.asLong(
                    cell.offset.x + face.offsetX,
                    cell.offset.y + face.offsetY,
                    cell.offset.z + face.offsetZ,
                )
                !positionSet.contains(neighborKey)
            }
        }
    }

    private fun downsampleCells(cells: List<ClipboardCell>, maxCells: Int): List<ClipboardCell> {
        if (cells.size <= maxCells) return cells
        val result = ArrayList<ClipboardCell>(maxCells)
        val lastIndex = cells.lastIndex
        for (index in 0 until maxCells) {
            val sourceIndex = ((index.toLong() * lastIndex) / (maxCells - 1).coerceAtLeast(1)).toInt()
            result += cells[sourceIndex]
        }
        return result
    }
}

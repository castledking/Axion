package axion.client.render

import axion.client.render.gpu.PreviewStateHalo
import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
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
 * Compiles reusable preview meshes for chunked preview rendering.
 */
object AxionPreviewMeshCache {
    private const val MAX_CACHE_SIZE = 32

    private data class CacheKey(
        val clipboard: ClipboardBuffer,
        val surfaceClipboard: ClipboardBuffer,
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
        surfaceClipboard: ClipboardBuffer = ClipboardSelectionRenderer.surfaceClipboard(clipboard),
        color: Int,
        alpha: Int,
        scale: Float,
        maxBlocks: Int = 1536,
    ): CachedMesh? {
        val occupiedCells = clipboard.nonAirCells()
        val surfaceCells = surfaceClipboard.nonAirCells()
        if (occupiedCells.isEmpty() || surfaceCells.isEmpty() || origins.isEmpty()) {
            return null
        }

        val maxOrigins = maxOf(1, maxBlocks / surfaceCells.size.coerceAtLeast(1))
        val boundedOrigins = origins.asSequence().take(maxOrigins).toList()
        if (boundedOrigins.isEmpty()) {
            return null
        }

        val originKeys = boundedOrigins.map { it.asLong() }
        val key = CacheKey(
            clipboard = clipboard,
            surfaceClipboard = surfaceClipboard,
            originKeys = originKeys,
        )

        return synchronized(cache) {
            cache.getOrPut(key) {
                buildMesh(occupiedCells, surfaceCells, boundedOrigins, maxBlocks)
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
        surfaceCells: List<ClipboardCell>,
        origins: List<BlockPos>,
        maxBlocks: Int,
    ): CachedMesh {
        val cellsToRender = if (surfaceCells.size * origins.size <= maxBlocks) {
            surfaceCells
        } else {
            downsampleCells(surfaceCells, maxOf(1, maxBlocks / origins.size.coerceAtLeast(1)))
        }
        val stateHalo = PreviewStateHalo.retain(occupiedCells, cellsToRender)

        val blocks = ArrayList<PreviewBlockInfo>(cellsToRender.size * origins.size)
        val statesByPosition = LinkedHashMap<Long, BlockState>(stateHalo.size * origins.size)

        origins.forEach { origin ->
            stateHalo.forEach { cell ->
                val pos = cell.absolutePos(origin)
                statesByPosition[pos.asLong()] = cell.state
            }
        }

        origins.forEach { origin ->
            cellsToRender.forEach { cell ->
                val pos = cell.absolutePos(origin)
                statesByPosition[pos.asLong()] = cell.state
                if (cell.state.renderType == BlockRenderType.MODEL) {
                    blocks += PreviewBlockInfo(pos = pos, state = cell.state)
                }
            }
        }

        return CachedMesh(blocks = blocks, statesByPosition = statesByPosition)
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

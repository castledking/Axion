package axion.client.render.gpu

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import axion.client.compat.MutableBlockPos
import axion.client.compat.unpackLongX
import axion.client.compat.unpackLongY
import axion.client.compat.unpackLongZ

/**
 * Per-section cache of surface-cell metadata, kept in lockstep with a
 * [ChunkedBooleanStore] via dirty-section tracking.
 *
 * One entry per occupied 16³ section:
 *   - `surfaceCells`: packed `BlockPos` longs for cells with at least one
 *     exposed face (interior cells are filtered out by [ChunkMeshTessellator]).
 *   - `bounds`: world-space AABB covering the section, for frustum culling.
 *
 * The cache is rebuilt only for sections marked dirty in the store. Tools
 * call [refreshDirty] after mutating the store; renderers iterate
 * [allEntries] each frame.
 */
class ChunkPreviewMeshCache {
    private val entries = Long2ObjectOpenHashMap<SectionEntry>()

    /**
     * Surface-cell + bounds metadata for a single 16³ section.
     */
    data class SectionEntry(
        val sectionKey: Long,
        val surfaceCells: LongArray,
        val bounds: Box,
    )

    val sectionCount: Int get() = entries.size

    /**
     * Total surface cells across all sections — what gets emitted per frame
     * (modulo frustum culling). Used to drop the legacy MAX_GHOST_BLOCKS cap
     * cleanly: massive previews simply produce more sections instead of
     * being downsampled.
     */
    fun totalSurfaceCells(): Int {
        var total = 0
        val iter = entries.values.iterator()
        while (iter.hasNext()) total += iter.next().surfaceCells.size
        return total
    }

    /**
     * Rebuild surface-cell lists for any sections marked dirty in [store].
     * Drains the store's dirty set as a side effect.
     */
    fun refreshDirty(store: ChunkedBooleanStore) {
        val dirty = store.consumeDirty()
        if (dirty.isEmpty()) return
        val iter = dirty.iterator()
        while (iter.hasNext()) {
            val key = iter.nextLong()
            val rawSection = store.rawSection(key)
            if (rawSection == null) {
                // Section was emptied — drop the cache entry.
                entries.remove(key)
                continue
            }
            val surface = ChunkMeshTessellator.buildSectionSurface(store, key)
            if (surface.isEmpty()) {
                entries.remove(key)
            } else {
                val bounds = sectionBounds(key)
                entries.put(key, SectionEntry(key, surface, bounds))
            }
        }
    }

    /** Force every section to rebuild on the next [refreshDirty] call. */
    fun invalidateAll(store: ChunkedBooleanStore) {
        store.markAllDirty()
    }

    /** Read-only iterator over cached section entries. */
    fun allEntries(): Iterable<SectionEntry> = entries.values

    /**
     * Drop everything (e.g. when the world unloads or the user clears a tool).
     * Doesn't touch the underlying store.
     */
    fun clear() {
        entries.clear()
    }

    /** Compute the world-space bounding box for a 16³ section. */
    private fun sectionBounds(sectionKey: Long): Box {
        val sx = ChunkedBooleanStore.sectionX(sectionKey) shl 4
        val sy = ChunkedBooleanStore.sectionY(sectionKey) shl 4
        val sz = ChunkedBooleanStore.sectionZ(sectionKey) shl 4
        return Box(
            sx.toDouble(), sy.toDouble(), sz.toDouble(),
            (sx + 16).toDouble(), (sy + 16).toDouble(), (sz + 16).toDouble(),
        )
    }
}

/**
 * Convenience: unpacks a packed-long surface-cell into a [MutableBlockPos]
 * without allocating. Hot loops should reuse a single MutableBlockPos across all
 * cells in a section.
 */
fun MutableBlockPos.setFromPacked(packed: Long): MutableBlockPos {
    return this.set(unpackLongX(packed), unpackLongY(packed), unpackLongZ(packed))
}

/**
 * State-by-position lookup for textured ghost rendering. Tools build this
 * alongside the [ChunkedBooleanStore] when populating it from a clipboard.
 *
 * Stored separately from the boolean store so the bit-packed layout stays
 * compact (no need to pack BlockState into 16 bits).
 */
class ChunkedStateMap {
    private val states = Long2ObjectOpenHashMap<BlockState>()

    fun put(x: Int, y: Int, z: Int, state: BlockState) {
        states.put(BlockPos.asLong(x, y, z), state)
    }

    fun put(pos: BlockPos, state: BlockState) {
        states.put(pos.asLong(), state)
    }

    fun get(packedPos: Long): BlockState? = states.get(packedPos)

    fun remove(packedPos: Long): BlockState? = states.remove(packedPos)

    fun clear() = states.clear()

    val size: Int get() = states.size

    /**
     * Read-only view as a `Map<Long, BlockState>`, backed by the underlying
     * fastutil map; no copy.
     */
    fun asMap(): Map<Long, BlockState> = states
}

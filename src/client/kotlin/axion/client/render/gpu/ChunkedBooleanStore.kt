package axion.client.render.gpu

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import axion.client.compat.unpackLongX
import axion.client.compat.unpackLongY
import axion.client.compat.unpackLongZ

/**
 * Bit-packed positional store for chunked preview rendering.
 *
 * Mirrors Axiom's `ChunkedBooleanRegion` storage:
 *   - Positions are grouped into 16x16x16 sections keyed by chunk-section coords.
 *   - Each section holds a `short[256]` indexed as `[y + z*16]`. Bit `x` of that
 *     short marks whether `(x,y,z)` is occupied within the section.
 *
 * Operations are O(1) for add/remove/contains and O(n) for iteration.
 *
 * Dirty chunk tracking lets renderers rebuild only the sections that changed,
 * which is the foundation for the GPU mesh cache (Phase B). When a position
 * is added/removed at a section boundary, neighboring sections are marked
 * dirty too — their mesh hidden-face elimination depends on what's adjacent.
 */
class ChunkedBooleanStore {
    /** sectionKey -> short[256] (one bit per cell in the 16x16x16 section). */
    private val sections = Long2ObjectOpenHashMap<ShortArray>()

    /** Sections that need their mesh rebuilt. Cleared by `consumeDirty`. */
    private val dirtySections = LongOpenHashSet()

    /** Total occupied cell count across all sections. */
    var size: Int = 0
        private set

    fun isEmpty(): Boolean = size == 0

    fun isNotEmpty(): Boolean = size > 0

    /** Add a position. Returns true if newly added. Marks the section + 6 neighbors dirty. */
    fun add(x: Int, y: Int, z: Int): Boolean {
        val sectionKey = sectionKey(x, y, z)
        val section = sections.getOrPut(sectionKey) { ShortArray(256) }
        val localY = y and 0xF
        val localZ = z and 0xF
        val mask = (1 shl (x and 0xF))
        val index = localY + localZ * 16
        val before = section[index].toInt()
        if ((before and mask) != 0) return false
        section[index] = (before or mask).toShort()
        size++
        markChunkAndNeighborsDirty(sectionKey, x, y, z)
        return true
    }

    fun add(pos: BlockPos): Boolean = add(pos.x, pos.y, pos.z)

    /** Remove a position. Returns true if it was present. Marks the section + 6 neighbors dirty. */
    fun remove(x: Int, y: Int, z: Int): Boolean {
        val sectionKey = sectionKey(x, y, z)
        val section = sections.get(sectionKey) ?: return false
        val localY = y and 0xF
        val localZ = z and 0xF
        val mask = (1 shl (x and 0xF))
        val index = localY + localZ * 16
        val before = section[index].toInt()
        if ((before and mask) == 0) return false
        section[index] = (before and mask.inv()).toShort()
        size--
        markChunkAndNeighborsDirty(sectionKey, x, y, z)

        // Free fully empty sections (keeps memory bounded).
        if (sectionIsEmpty(section)) {
            sections.remove(sectionKey)
        }
        return true
    }

    fun contains(x: Int, y: Int, z: Int): Boolean {
        val section = sections.get(sectionKey(x, y, z)) ?: return false
        val mask = (1 shl (x and 0xF))
        return (section[(y and 0xF) + (z and 0xF) * 16].toInt() and mask) != 0
    }

    fun clear() {
        if (sections.isEmpty()) return
        // Mark every section dirty so renderers free their meshes.
        sections.keys.forEach { dirtySections.add(it) }
        sections.clear()
        size = 0
    }

    /**
     * Read raw section data. Returns `null` if the section is empty.
     * Used by [ChunkMeshTessellator] for cross-section neighbor lookups.
     */
    fun rawSection(sectionKey: Long): ShortArray? = sections.get(sectionKey)

    fun rawSection(sectionX: Int, sectionY: Int, sectionZ: Int): ShortArray? =
        sections.get(BlockPos.asLong(sectionX, sectionY, sectionZ))

    /** Iterate every populated section; useful for full rebuild. */
    fun forEachSection(action: (sectionKey: Long, section: ShortArray) -> Unit) {
        val iter = sections.long2ObjectEntrySet().fastIterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            action(entry.longKey, entry.value)
        }
    }

    /** Currently-dirty section keys (read-only snapshot). */
    fun dirtySnapshot(): LongOpenHashSet = LongOpenHashSet(dirtySections)

    /** Drain the dirty set, returning a snapshot. */
    fun consumeDirty(): LongOpenHashSet {
        val snapshot = LongOpenHashSet(dirtySections)
        dirtySections.clear()
        return snapshot
    }

    /** Mark every section dirty, e.g. after a global parameter change. */
    fun markAllDirty() {
        sections.keys.forEach { dirtySections.add(it) }
    }

    private fun markChunkAndNeighborsDirty(sectionKey: Long, x: Int, y: Int, z: Int) {
        dirtySections.add(sectionKey)
        // If the cell is on a section face, the neighbor section's outermost
        // mesh layer might have a face whose visibility just changed.
        val sx = x shr 4
        val sy = y shr 4
        val sz = z shr 4
        val lx = x and 0xF
        val ly = y and 0xF
        val lz = z and 0xF
        if (lx == 0) dirtySections.add(BlockPos.asLong(sx - 1, sy, sz))
        if (lx == 15) dirtySections.add(BlockPos.asLong(sx + 1, sy, sz))
        if (ly == 0) dirtySections.add(BlockPos.asLong(sx, sy - 1, sz))
        if (ly == 15) dirtySections.add(BlockPos.asLong(sx, sy + 1, sz))
        if (lz == 0) dirtySections.add(BlockPos.asLong(sx, sy, sz - 1))
        if (lz == 15) dirtySections.add(BlockPos.asLong(sx, sy, sz + 1))
    }

    private fun sectionIsEmpty(section: ShortArray): Boolean {
        for (s in section) {
            if (s != 0.toShort()) return false
        }
        return true
    }

    companion object {
        fun sectionKey(x: Int, y: Int, z: Int): Long = BlockPos.asLong(x shr 4, y shr 4, z shr 4)
        fun sectionX(key: Long): Int = unpackLongX(key)
        fun sectionY(key: Long): Int = unpackLongY(key)
        fun sectionZ(key: Long): Int = unpackLongZ(key)
    }
}

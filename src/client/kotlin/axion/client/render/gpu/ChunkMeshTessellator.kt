package axion.client.render.gpu

import net.minecraft.util.math.BlockPos

/**
 * Builds per-section "exposed surface" lists from a [ChunkedBooleanStore].
 *
 * For each occupied cell in a section, all 6 face neighbors are checked
 * (within-section by bit math, cross-section by reading the adjacent
 * section's outermost layer). A cell is on the surface if at least one
 * of its faces is exposed (neighbor empty).
 *
 * The output is a `LongArray` of packed `BlockPos` values. Cell positions
 * (not faces) are returned because the consumer ([axion.client.render.AxionBlockTessellator])
 * needs the cell to look up the block model. Hidden-face culling at the
 * face level then happens automatically inside the block tessellator's
 * `checkSides` path.
 *
 * Cross-section neighbor lookups pre-fetch the 6 adjacent section arrays
 * once, so each cell's face checks are pure bit math.
 */
object ChunkMeshTessellator {

    /**
     * Builds the surface-cell list for a single section.
     *
     * Returns an empty array if the section is empty. Cells whose 6 face
     * neighbors are all occupied (interior cells) are filtered out — those
     * never need rendering for ghost previews, since they're hidden.
     */
    fun buildSectionSurface(store: ChunkedBooleanStore, sectionKey: Long): LongArray {
        val section = store.rawSection(sectionKey) ?: return EMPTY
        val sectionX = ChunkedBooleanStore.sectionX(sectionKey)
        val sectionY = ChunkedBooleanStore.sectionY(sectionKey)
        val sectionZ = ChunkedBooleanStore.sectionZ(sectionKey)

        // Pre-fetch the 6 neighbor sections once. `null` means empty section,
        // i.e. every cell on that face is exposed.
        val east = store.rawSection(sectionX - 1, sectionY, sectionZ)
        val west = store.rawSection(sectionX + 1, sectionY, sectionZ)
        val down = store.rawSection(sectionX, sectionY - 1, sectionZ)
        val up = store.rawSection(sectionX, sectionY + 1, sectionZ)
        val north = store.rawSection(sectionX, sectionY, sectionZ - 1)
        val south = store.rawSection(sectionX, sectionY, sectionZ + 1)

        val baseX = sectionX shl 4
        val baseY = sectionY shl 4
        val baseZ = sectionZ shl 4

        // Worst case: every cell is on the surface (4096). Most chunks have far fewer.
        val out = LongArray(4096)
        var count = 0

        for (z in 0..15) {
            for (y in 0..15) {
                val v = section[y + z * 16].toInt()
                if (v == 0) continue
                for (x in 0..15) {
                    val mask = 1 shl x
                    if ((v and mask) == 0) continue
                    if (!isInterior(section, east, west, down, up, north, south, x, y, z, mask, v)) {
                        out[count++] = BlockPos.asLong(baseX + x, baseY + y, baseZ + z)
                    }
                }
            }
        }

        return if (count == 0) EMPTY else out.copyOf(count)
    }

    /**
     * Iterates surface cells in a section, calling [action] with the absolute
     * world position of each. Avoids array allocation when the caller only
     * needs to iterate.
     */
    inline fun forEachSurfaceCell(
        store: ChunkedBooleanStore,
        sectionKey: Long,
        action: (x: Int, y: Int, z: Int) -> Unit,
    ) {
        val section = store.rawSection(sectionKey) ?: return
        val sectionX = ChunkedBooleanStore.sectionX(sectionKey)
        val sectionY = ChunkedBooleanStore.sectionY(sectionKey)
        val sectionZ = ChunkedBooleanStore.sectionZ(sectionKey)

        val east = store.rawSection(sectionX - 1, sectionY, sectionZ)
        val west = store.rawSection(sectionX + 1, sectionY, sectionZ)
        val down = store.rawSection(sectionX, sectionY - 1, sectionZ)
        val up = store.rawSection(sectionX, sectionY + 1, sectionZ)
        val north = store.rawSection(sectionX, sectionY, sectionZ - 1)
        val south = store.rawSection(sectionX, sectionY, sectionZ + 1)

        val baseX = sectionX shl 4
        val baseY = sectionY shl 4
        val baseZ = sectionZ shl 4

        for (z in 0..15) {
            for (y in 0..15) {
                val v = section[y + z * 16].toInt()
                if (v == 0) continue
                for (x in 0..15) {
                    val mask = 1 shl x
                    if ((v and mask) == 0) continue
                    if (!isInterior(section, east, west, down, up, north, south, x, y, z, mask, v)) {
                        action(baseX + x, baseY + y, baseZ + z)
                    }
                }
            }
        }
    }

    /**
     * Returns true iff every face of cell (x,y,z) within `section` is occupied
     * (so the cell has no exposed faces and can be skipped for previews).
     *
     * `@PublishedApi internal` so the public inline function above can reach it.
     */
    @PublishedApi
    internal fun isInterior(
        section: ShortArray,
        east: ShortArray?,
        west: ShortArray?,
        down: ShortArray?,
        up: ShortArray?,
        north: ShortArray?,
        south: ShortArray?,
        x: Int, y: Int, z: Int,
        mask: Int, v: Int,
    ): Boolean {
        // -X (toward -X means smaller X). At x=0 we cross into the `east` neighbor.
        val minusXOccupied = if (x == 0) {
            east != null && (east[y + z * 16].toInt() and 0x8000) != 0
        } else {
            (v and (mask shr 1)) != 0
        }
        if (!minusXOccupied) return false

        // +X
        val plusXOccupied = if (x == 15) {
            west != null && (west[y + z * 16].toInt() and 0x0001) != 0
        } else {
            (v and (mask shl 1)) != 0
        }
        if (!plusXOccupied) return false

        // -Y
        val minusYOccupied = if (y == 0) {
            down != null && (down[15 + z * 16].toInt() and mask) != 0
        } else {
            (section[(y - 1) + z * 16].toInt() and mask) != 0
        }
        if (!minusYOccupied) return false

        // +Y
        val plusYOccupied = if (y == 15) {
            up != null && (up[z * 16].toInt() and mask) != 0
        } else {
            (section[(y + 1) + z * 16].toInt() and mask) != 0
        }
        if (!plusYOccupied) return false

        // -Z
        val minusZOccupied = if (z == 0) {
            north != null && (north[y + 15 * 16].toInt() and mask) != 0
        } else {
            (section[y + (z - 1) * 16].toInt() and mask) != 0
        }
        if (!minusZOccupied) return false

        // +Z
        val plusZOccupied = if (z == 15) {
            south != null && (south[y].toInt() and mask) != 0
        } else {
            (section[y + (z + 1) * 16].toInt() and mask) != 0
        }
        return plusZOccupied
    }

    /** Counts occupied cells in a section without iterating bits one-at-a-time. */
    fun countOccupied(store: ChunkedBooleanStore, sectionKey: Long): Int {
        val section = store.rawSection(sectionKey) ?: return 0
        var count = 0
        for (s in section) {
            count += Integer.bitCount(s.toInt() and 0xFFFF)
        }
        return count
    }

    private val EMPTY = LongArray(0)
}

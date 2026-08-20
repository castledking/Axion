package axion.client.render.gpu

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos

/**
 * Builds per-section "exposed surface" lists from a [ChunkedBooleanStore].
 *
 * For each occupied cell in a section, all 6 face neighbors are checked
 * (within-section by bit math, cross-section by reading the adjacent
 * section's outermost layer). A cell is on the surface if at least one
 * of its faces is exposed — meaning the neighbor is either empty or
 * non-opaque (e.g. short_grass, flowers, crops).
 *
 * The output is a `LongArray` of packed `BlockPos` values. Cell positions
 * (not faces) are returned because the version-specific preview renderer
 * needs the cell to look up the block model. Hidden-face culling at the
 * face level then happens automatically inside the block tessellator path.
 *
 * Cross-section neighbor lookups pre-fetch the 6 adjacent section arrays
 * once, so each cell's face checks are pure bit math. When a neighbor IS
 * present, a state-map lookup determines if it actually occludes the face
     * via the version-compatible opaque-full-cube state (cached per state).
 */
object ChunkMeshTessellator {

    /**
     * Builds the surface-cell list for a single section.
     *
     * Returns an empty array if the section is empty. Cells whose 6 face
     * neighbors are all occupied by opaque-full-cube blocks are filtered
     * out — those never need rendering for ghost previews, since they're
     * fully hidden. Non-opaque neighbors (short_grass, flowers, crops,
     * etc.) do NOT count as occluders.
     */
    fun buildSectionSurface(
        store: ChunkedBooleanStore,
        sectionKey: Long,
        statesByPosition: Map<Long, BlockState>? = null,
    ): LongArray {
        val section = store.rawSection(sectionKey) ?: return EMPTY
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

        val out = LongArray(4096)
        var count = 0

        for (z in 0..15) {
            for (y in 0..15) {
                val v = section[y + z * 16].toInt()
                if (v == 0) continue
                for (x in 0..15) {
                    val mask = 1 shl x
                    if ((v and mask) == 0) continue
                    val pos = BlockPos.asLong(baseX + x, baseY + y, baseZ + z)
                    if (!isInterior(section, east, west, down, up, north, south, x, y, z, mask, v,
                            statesByPosition, baseX, baseY, baseZ)) {
                        out[count++] = pos
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
                    if (!isInterior(section, east, west, down, up, north, south, x, y, z, mask, v,
                            null, baseX, baseY, baseZ)) {
                        action(baseX + x, baseY + y, baseZ + z)
                    }
                }
            }
        }
    }

    /**
     * Returns true iff every face of cell (x,y,z) is occluded by an opaque
     * full cube neighbor. When [statesByPosition] is provided, a neighbor
     * only counts as an occluder if its [BlockState.isOpaqueFullCube] is
     * true. Without state data, falls back to presence-only checks.
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
        statesByPosition: Map<Long, BlockState>?,
        baseX: Int, baseY: Int, baseZ: Int,
    ): Boolean {
        // -X
        val minusXPresent = if (x == 0) {
            east != null && (east[y + z * 16].toInt() and 0x8000) != 0
        } else {
            (v and (mask shr 1)) != 0
        }
        if (!minusXPresent) return false
        if (statesByPosition != null && !isOpaqueAt(statesByPosition, baseX + x - 1, baseY + y, baseZ + z)) return false

        // +X
        val plusXPresent = if (x == 15) {
            west != null && (west[y + z * 16].toInt() and 0x0001) != 0
        } else {
            (v and (mask shl 1)) != 0
        }
        if (!plusXPresent) return false
        if (statesByPosition != null && !isOpaqueAt(statesByPosition, baseX + x + 1, baseY + y, baseZ + z)) return false

        // -Y
        val minusYPresent = if (y == 0) {
            down != null && (down[15 + z * 16].toInt() and mask) != 0
        } else {
            (section[(y - 1) + z * 16].toInt() and mask) != 0
        }
        if (!minusYPresent) return false
        if (statesByPosition != null && !isOpaqueAt(statesByPosition, baseX + x, baseY + y - 1, baseZ + z)) return false

        // +Y - Check if there's a non-opaque block above, if so the block is NOT interior
        // This fixes grass blocks being culled when short grass/flowers are on top
        val plusYPresent = if (y == 15) {
            up != null && (up[z * 16].toInt() and mask) != 0
        } else {
            (section[(y + 1) + z * 16].toInt() and mask) != 0
        }
        if (!plusYPresent) return false
        if (statesByPosition != null) {
            val aboveState = statesByPosition[BlockPos.asLong(baseX + x, baseY + y + 1, baseZ + z)]
            // If there's a non-opaque block above (e.g., short grass, flowers), the block below is NOT interior
            if (aboveState != null && !isOpaqueFullCubeCompat(aboveState)) {
                return false
            }
            if (!isOpaqueAt(statesByPosition, baseX + x, baseY + y + 1, baseZ + z)) return false
        }

        // -Z
        val minusZPresent = if (z == 0) {
            north != null && (north[y + 15 * 16].toInt() and mask) != 0
        } else {
            (section[y + (z - 1) * 16].toInt() and mask) != 0
        }
        if (!minusZPresent) return false
        if (statesByPosition != null && !isOpaqueAt(statesByPosition, baseX + x, baseY + y, baseZ + z - 1)) return false

        // +Z
        val plusZPresent = if (z == 15) {
            south != null && (south[y].toInt() and mask) != 0
        } else {
            (section[y + (z + 1) * 16].toInt() and mask) != 0
        }
        if (!plusZPresent) return false
        if (statesByPosition != null && !isOpaqueAt(statesByPosition, baseX + x, baseY + y, baseZ + z + 1)) return false

        return true
    }

    private fun isOpaqueAt(statesByPosition: Map<Long, BlockState>, x: Int, y: Int, z: Int): Boolean {
        val state = statesByPosition[BlockPos.asLong(x, y, z)] ?: return false
        return isOpaqueFullCubeCompat(state)
    }

    private fun isOpaqueFullCubeCompat(state: BlockState): Boolean {
        return PreviewOcclusionCompat.isOpaqueFullCube(state)
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

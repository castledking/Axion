package axion.client.render

import axion.common.model.ClipboardBuffer
import net.minecraft.block.BlockState
import java.util.WeakHashMap

/**
 * Keeps preview rendering from treating server-obfuscated block identities as
 * authoritative while preserving the captured air/non-air occupancy.
 */
object PreviewBlockIdentityPolicy {
    const val MOVE_SOURCE_SESSION_TAG: String = "move-source-replacement"

    private data class CachedClipboard(
        val neutralState: BlockState,
        val clipboard: ClipboardBuffer,
    )

    private val normalizedClipboardCache = WeakHashMap<ClipboardBuffer, CachedClipboard>()

    fun <T> resolve(
        original: T,
        neutral: T,
        isAir: Boolean,
        remoteAxionSessionAvailable: Boolean,
    ): T {
        return if (remoteAxionSessionAvailable && !isAir) neutral else original
    }

    /** Move-source geometry is already deliberately normalized to glass. */
    fun shouldNormalizeRemoteIdentity(
        remoteAxionSessionAvailable: Boolean,
        sessionTag: String,
    ): Boolean = remoteAxionSessionAvailable && sessionTag != MOVE_SOURCE_SESSION_TAG

    /**
     * Whether a preview's mesh should be tessellated with ambient occlusion.
     *
     * The preview shell shader carries no lightmap, so a preview's only shading
     * is its baked vertex colour: face shade multiplied by ambient occlusion.
     * That makes AO the sole source of darkness in a preview, and it is computed
     * against the full-occupancy neighbour halo the renderers keep purely so
     * vanilla culls shared faces. For the move-source glass the halo is the
     * replaced volume itself, so every boundary face is treated as buried and
     * the replacement renders as a near-black slab over lit terrain.
     *
     * Disabling AO there lets the glass read as glass with sky light behind it.
     * Destination ghosts keep AO: their halo is the shape being previewed, and
     * the self-shadowing is what makes the shape legible in mid-air.
     */
    fun usesAmbientOcclusion(previewId: String): Boolean =
        !previewId.endsWith(MOVE_SOURCE_SESSION_TAG)

    @Synchronized
    fun normalize(
        clipboard: ClipboardBuffer,
        neutralState: BlockState,
        remoteAxionSessionAvailable: Boolean,
    ): ClipboardBuffer {
        if (!remoteAxionSessionAvailable) {
            return clipboard
        }

        normalizedClipboardCache[clipboard]
            ?.takeIf { it.neutralState == neutralState }
            ?.let { return it.clipboard }

        var changed = false
        val cells = clipboard.cells.map { cell ->
            val resolvedState = resolve(
                original = cell.state,
                neutral = neutralState,
                isAir = cell.state.isAir,
                remoteAxionSessionAvailable = remoteAxionSessionAvailable,
            )
            if (resolvedState == cell.state && cell.blockEntityData == null) {
                cell
            } else {
                changed = true
                cell.copy(
                    state = resolvedState,
                    blockEntityData = null,
                )
            }
        }
        val normalized = if (changed) {
            ClipboardBuffer(
                size = clipboard.size,
                cells = cells,
            )
        } else {
            clipboard
        }
        normalizedClipboardCache[clipboard] = CachedClipboard(
            neutralState = neutralState,
            clipboard = normalized,
        )
        return normalized
    }
}

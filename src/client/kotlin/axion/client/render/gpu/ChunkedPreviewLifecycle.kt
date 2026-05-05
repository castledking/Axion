package axion.client.render.gpu

import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry for [ChunkedPreviewSession]s.
 *
 * Sessions are looked up by stable string ID (one per tool: "move", "stack",
 * "clone", "magic-select", etc). Tools fetch their session via [acquire]
 * and never release it — sessions live for the lifetime of the world.
 *
 * On world unload (called from `AxionTickHandler` or a Fabric lifecycle event),
 * [closeAll] frees all session resources, ensuring stale chunks from a previous
 * world don't leak into the next dimension/save.
 */
object ChunkedPreviewLifecycle {
    private val sessions = ConcurrentHashMap<String, ChunkedPreviewSession>()

    /**
     * Get-or-create the session for [previewId]. Stable identity across calls.
     */
    fun acquire(previewId: String): ChunkedPreviewSession {
        return sessions.computeIfAbsent(previewId) { ChunkedPreviewSession(it) }
    }

    /** Optional explicit drop, e.g. when a tool is removed from the loadout. */
    fun release(previewId: String) {
        sessions.remove(previewId)?.close()
    }

    /**
     * Free every session's GPU/CPU resources. Called on world unload and
     * dimension change. Subsequent [acquire] calls will re-create empty
     * sessions on demand.
     */
    fun closeAll() {
        val snapshot = ArrayList(sessions.values)
        sessions.clear()
        snapshot.forEach { it.close() }
    }

    fun activeSessionCount(): Int = sessions.size

    /** Aggregate diagnostic — total cached surface cells across all sessions. */
    fun totalCachedSurfaceCells(): Int {
        var total = 0
        sessions.values.forEach { total += it.totalSurfaceCells() }
        return total
    }
}

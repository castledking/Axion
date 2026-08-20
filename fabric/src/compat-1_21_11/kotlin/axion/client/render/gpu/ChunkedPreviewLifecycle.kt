package axion.client.render.gpu

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.slf4j.LoggerFactory

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
    private val logger = LoggerFactory.getLogger(ChunkedPreviewLifecycle::class.java)
    private const val DEBUG_LOG: Boolean = false
    private const val LOG_INTERVAL_MS: Long = 1000
    private var lastLogTime: Long = 0
    private val sessions = ConcurrentHashMap<String, ChunkedPreviewSession>()
    private val deferredDraws = ArrayList<DeferredDraw>()

    private data class DeferredDraw(
        val session: ChunkedPreviewSession,
        val color: Int,
        val alpha: Int,
        val translationDelta: Vec3i,
        val baseModelView: Matrix4f,
        val cameraPos: Vec3d?,
    )

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
        synchronized(deferredDraws) {
            deferredDraws.clear()
        }
        snapshot.forEach { it.close() }
    }

    fun enqueueDeferredDraw(
        session: ChunkedPreviewSession,
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
        baseModelView: Matrix4f,
        cameraPos: Vec3d? = null,
    ) {
        synchronized(deferredDraws) {
            deferredDraws += DeferredDraw(session, color, alpha, translationDelta, Matrix4f(baseModelView), cameraPos)
        }
    }

    fun flushDeferredDraws(cullingModelView: Matrix4fc? = null, projectionMatrix: Matrix4fc? = null) {
        val pending = synchronized(deferredDraws) {
            if (deferredDraws.isEmpty()) return
            ArrayList(deferredDraws).also { deferredDraws.clear() }
        }
        if (DEBUG_LOG) {
            val now = System.currentTimeMillis()
            if (now - lastLogTime >= LOG_INTERVAL_MS) {
                lastLogTime = now
                logger.info(
                    "[Axion diag] deferred flush: draws={} sessions={} hasCullingMatrix={} hasProjection={}",
                    pending.size,
                    sessions.size,
                    cullingModelView != null,
                    projectionMatrix != null,
                )
            }
        }
        for (draw in pending) {
            draw.session.drawDeferred(
                draw.color,
                draw.alpha,
                draw.translationDelta,
                draw.baseModelView,
                draw.cameraPos,
                cullingModelView,
                projectionMatrix,
            )
        }
    }

    fun activeSessionCount(): Int = sessions.size

    /** Aggregate diagnostic — total cached surface cells across all sessions. */
    fun totalCachedSurfaceCells(): Int {
        var total = 0
        sessions.values.forEach { total += it.totalSurfaceCells() }
        return total
    }
}

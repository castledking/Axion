package axion.client.render.gpu

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.slf4j.LoggerFactory

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

    fun acquire(previewId: String): ChunkedPreviewSession {
        return sessions.computeIfAbsent(previewId) { ChunkedPreviewSession(it) }
    }

    fun release(previewId: String) {
        sessions.remove(previewId)?.close()
    }

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

    fun totalCachedSurfaceCells(): Int {
        var total = 0
        sessions.values.forEach { total += it.totalSurfaceCells() }
        return total
    }
}

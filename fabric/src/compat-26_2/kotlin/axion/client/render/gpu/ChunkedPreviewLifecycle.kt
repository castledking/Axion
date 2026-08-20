package axion.client.render.gpu

import axion.client.render.PreviewVisualPolicy
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.textures.GpuTextureView
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4f
import org.slf4j.LoggerFactory

object ChunkedPreviewLifecycle {
    private val logger = LoggerFactory.getLogger(ChunkedPreviewLifecycle::class.java)
    private val sessions = ConcurrentHashMap<String, ChunkedPreviewSession>()
    private val deferredDraws = ArrayList<DeferredDraw>()
    private val preservedSceneDepth = PreservedSceneDepth()
    private var queuedSceneDepth: GpuTextureView? = null
    private var loggedPostWorldFlush = false
    private var loggedMissingSceneDepth = false

    private data class DeferredDraw(
        val session: ChunkedPreviewSession,
        val color: Int,
        val alpha: Int,
        val translationDelta: Vec3i,
        val baseModelView: Matrix4f,
        val cameraPos: Vec3d?,
        val projection: GpuBufferSlice,
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
            queuedSceneDepth = null
        }
        preservedSceneDepth.close()
        snapshot.forEach { it.close() }
    }

    fun enqueuePostWorldDraw(
        session: ChunkedPreviewSession,
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
        baseModelView: Matrix4f,
        cameraPos: Vec3d?,
        projection: GpuBufferSlice,
    ): Boolean {
        val mainTarget = MinecraftClient.getInstance().framebuffer
        if (mainTarget.depthTexture == null || mainTarget.depthTextureView == null) return false
        synchronized(deferredDraws) {
            if (deferredDraws.isEmpty()) queuedSceneDepth = null
            deferredDraws += DeferredDraw(
                session,
                color,
                alpha,
                translationDelta,
                Matrix4f(baseModelView),
                cameraPos,
                projection,
            )
        }
        return true
    }

    /** Called after the world frame graph completes and before hand depth clears. */
    fun captureSceneDepthBeforeHand() {
        if (PreviewVisualPolicy.XRAY_BLOCK_PREVIEWS) return
        val hasPendingDraw = synchronized(deferredDraws) { deferredDraws.isNotEmpty() }
        if (!hasPendingDraw) return
        val captured = preservedSceneDepth.capture(MinecraftClient.getInstance().framebuffer)
        synchronized(deferredDraws) {
            if (deferredDraws.isNotEmpty()) queuedSceneDepth = captured
        }
    }

    fun flushPostWorldDraws() {
        val pendingWithDepth = synchronized(deferredDraws) {
            if (deferredDraws.isEmpty()) return
            val depth = if (PreviewVisualPolicy.XRAY_BLOCK_PREVIEWS) {
                MinecraftClient.getInstance().framebuffer.depthTextureView
            } else {
                queuedSceneDepth
            }
            val pending = ArrayList(deferredDraws)
            deferredDraws.clear()
            queuedSceneDepth = null
            if (depth == null) null else pending to depth
        }
        if (pendingWithDepth == null) {
            if (!loggedMissingSceneDepth) {
                loggedMissingSceneDepth = true
                logger.warn("[Axion GPU] Skipping deferred previews because the completed scene depth was unavailable.")
            }
            return
        }
        val (pending, sceneDepth) = pendingWithDepth
        if (!loggedPostWorldFlush) {
            loggedPostWorldFlush = true
            logger.info(
                "[Axion GPU] Deferred {} preview draw(s) past the entity-outline composite.",
                pending.size,
            )
        }
        for (draw in pending) {
            draw.session.drawPostWorld(
                draw.color,
                draw.alpha,
                draw.translationDelta,
                draw.baseModelView,
                draw.cameraPos,
                draw.projection,
                sceneDepth,
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

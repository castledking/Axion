package axion.client.render.gpu

import axion.client.compat.CameraAccess
import axion.client.render.AxionPreviewBuffer
import axion.client.render.RenderLayerCompat
import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.VertexBuffer
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.slf4j.LoggerFactory

object AxionPreviewBlockDrawer {
    private val logger = LoggerFactory.getLogger(AxionPreviewBlockDrawer::class.java)
    private const val MAX_FAILURES = 3
    private var failureCount = 0
    private var disabled = false
    private var loggedFirstSuccess = false

    fun isDisabled(): Boolean = disabled

    fun resetFailureState() {
        failureCount = 0
        disabled = false
        loggedFirstSuccess = false
    }

    fun drawChunked(
        sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>,
        color: Int,
        alpha: Int,
        translationDelta: Vec3i = Vec3i.ZERO,
        baseModelView: Matrix4fc? = null,
        cameraPosOverride: Vec3d? = null,
        cullingModelView: Matrix4fc? = null,
        projectionMatrix: Matrix4fc? = null,
    ): ChunkedDrawResult {
        if (sectionBuffers.isEmpty()) return ChunkedDrawResult.NO_BUFFERS
        if (disabled) return ChunkedDrawResult.FAILED

        return try {
            val result = drawLegacyGpu(
                sectionBuffers,
                color,
                alpha,
                translationDelta,
                baseModelView,
                cameraPosOverride,
                projectionMatrix,
            )
            if (result == ChunkedDrawResult.DREW) {
                failureCount = 0
                if (!loggedFirstSuccess) {
                    loggedFirstSuccess = true
                    logger.info("[Axion GPU] Legacy VertexBuffer preview drawer active; drew {} sections.", sectionBuffers.size)
                }
            }
            result
        } catch (throwable: Throwable) {
            failureCount += 1
            logger.warn(
                "[Axion GPU] Legacy VertexBuffer preview draw failed (attempt {} of {}).",
                failureCount,
                MAX_FAILURES,
                throwable,
            )
            if (failureCount >= MAX_FAILURES) {
                disabled = true
                logger.warn("[Axion GPU] Disabling legacy VertexBuffer previews after {} failures.", MAX_FAILURES)
            }
            ChunkedDrawResult.FAILED
        }
    }

    private fun drawLegacyGpu(
        sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>,
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
        baseModelView: Matrix4fc?,
        cameraPosOverride: Vec3d?,
        projectionMatrix: Matrix4fc?,
    ): ChunkedDrawResult {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return ChunkedDrawResult.FAILED
        val cameraPos = cameraPosOverride ?: CameraAccess.getPos(camera)
        val drawList = SectionDrawList.buildAll(sectionBuffers)
        if (drawList.isEmpty()) return ChunkedDrawResult.NO_BUFFERS

        // Fabric's legacy BEFORE_DEBUG_RENDER MatrixStack is identity. Direct
        // VertexBuffer draws do not inherit the camera rotation later applied
        // when buffered RenderLayers flush, so use Minecraft's active
        // model-view matrix here.
        val modelView = Matrix4f(RenderSystem.getModelViewMatrix())
        val projection = Matrix4f(projectionMatrix ?: RenderSystem.getProjectionMatrix())
        val previousColor = RenderSystem.getShaderColor().copyOf()
        val layer = RenderLayerCompat.blockTranslucentCull()
        var layerStarted = false
        try {
            layer.startDrawing()
            layerStarted = true
            RenderSystem.setShaderColor(
                ((color shr 16) and 0xFF) / 255f,
                ((color shr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f,
                (alpha and 0xFF) / 255f,
            )
            val shader = RenderSystem.getShader() ?: return ChunkedDrawResult.FAILED
            val sectionModelView = Matrix4f()
            for (entry in drawList) {
                sectionModelView.set(modelView)
                val translation = PreviewSectionTransform.cameraRelative(
                    entry.sectionOriginX,
                    entry.sectionOriginY,
                    entry.sectionOriginZ,
                    cameraPos.x,
                    cameraPos.y,
                    cameraPos.z,
                    translationDelta.x,
                    translationDelta.y,
                    translationDelta.z,
                )
                sectionModelView.translate(translation.x, translation.y, translation.z)
                entry.buffer.draw(sectionModelView, projection, shader)
            }
            return ChunkedDrawResult.DREW
        } finally {
            VertexBuffer.unbind()
            if (layerStarted) layer.endDrawing()
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3])
        }
    }
}

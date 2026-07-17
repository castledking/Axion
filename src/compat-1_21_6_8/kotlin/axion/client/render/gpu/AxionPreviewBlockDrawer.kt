package axion.client.render.gpu

import axion.client.compat.CameraAccess
import axion.client.compat.VersionCompatImpl
import axion.client.render.AxionPreviewBuffer
import axion.client.render.RenderLayerCompat
import axion.client.render.ShaderPackCompat
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.Frustum
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector4f
import org.slf4j.LoggerFactory
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.function.Supplier

/**
 * Direct GPU preview drawer for Minecraft 1.21.7/legacy range.
 *
 * 1.21.7 has the DynamicUniforms/GpuBufferSlice path used by 1.21.11, but
 * lacks the newer multi-draw texture/uniform helpers. This renderer keeps the
 * same cached chunk buffers and submits a manual per-section draw loop.
 */
object AxionPreviewBlockDrawer {
    private val logger = LoggerFactory.getLogger(AxionPreviewBlockDrawer::class.java)
    private const val DEBUG_LOG: Boolean = false
    private const val LOG_INTERVAL_MS: Long = 1000
    private const val USE_MULTI_DRAW: Boolean = false
    private const val USE_CUSTOM_PREVIEW_PIPELINE: Boolean = true
    private const val USE_SECTION_FRUSTUM_CULLING: Boolean = true
    private const val DEBUG_FORCE_FIXED_TRANSFORM: Boolean = false
    private const val DEBUG_CLEAR_DEPTH: Boolean = false
    private const val MAX_FAILURES: Int = 3

    private var lastLogTime: Long = 0
    private var failureCount: Int = 0
    private var disabled: Boolean = false
    private var loggedFirstSuccess: Boolean = false

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
        if (disabled || sectionBuffers.isEmpty()) return ChunkedDrawResult.FAILED
        if (ShaderPackCompat.shouldDisableDirectGpuPreview()) return ChunkedDrawResult.FAILED

        return try {
            val result = doDrawChunked(
                sectionBuffers,
                color,
                alpha,
                translationDelta,
                baseModelView,
                cameraPosOverride,
                cullingModelView,
                projectionMatrix,
            )
            if (result == ChunkedDrawResult.DREW && !loggedFirstSuccess) {
                loggedFirstSuccess = true
                logger.info(
                    "[Axion GPU 1.21.7] Preview drawer active - drew {} chunks via manual per-section draw.",
                    sectionBuffers.size,
                )
            }
            result
        } catch (t: Throwable) {
            failureCount++
            if (failureCount <= MAX_FAILURES) {
                logger.warn(
                    "[Axion GPU 1.21.7] Preview draw failed (attempt {} of {}). Falling back to CPU path for this frame.",
                    failureCount,
                    MAX_FAILURES,
                    t,
                )
            }
            if (failureCount >= MAX_FAILURES && !disabled) {
                disabled = true
                logger.warn(
                    "[Axion GPU 1.21.7] Disabling preview drawer after {} failures.",
                    MAX_FAILURES,
                )
            }
            ChunkedDrawResult.FAILED
        }
    }

    private fun doDrawChunked(
        sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>,
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
        baseModelView: Matrix4fc?,
        cameraPosOverride: Vec3d?,
        cullingModelView: Matrix4fc?,
        projectionMatrix: Matrix4fc?,
    ): ChunkedDrawResult {
        val client = MinecraftClient.getInstance()
        val device = RenderSystem.getDevice()
        val mainTarget = client.framebuffer ?: return ChunkedDrawResult.FAILED
        val colorView = mainTarget.colorAttachmentView ?: return ChunkedDrawResult.FAILED
        val depthView = mainTarget.depthAttachmentView ?: return ChunkedDrawResult.FAILED

        val camera = client.gameRenderer?.camera ?: return ChunkedDrawResult.FAILED
        val cameraPos = cameraPosOverride ?: CameraAccess.getPos(camera)
        val baseMv = if (USE_CUSTOM_PREVIEW_PIPELINE) {
            Matrix4f(RenderSystem.getModelViewMatrix())
        } else {
            Matrix4f(cullingModelView ?: baseModelView ?: RenderSystem.getModelViewMatrix())
        }
        val normalMatrix = Matrix4f(baseMv).invert().transpose()
        val frameStartNs = if (DEBUG_LOG) System.nanoTime() else 0L

        val useSectionFrustumCulling = USE_SECTION_FRUSTUM_CULLING && !USE_CUSTOM_PREVIEW_PIPELINE
        val drawList = if (useSectionFrustumCulling) {
            val proj = Matrix4f(projectionMatrix ?: client.gameRenderer?.getBasicProjectionMatrix(1.0f) ?: Matrix4f())
            val frustum = Frustum(Matrix4f(cullingModelView ?: baseMv), proj)
            frustum.setPosition(cameraPos.x, cameraPos.y, cameraPos.z)
            SectionDrawList.buildVisible(sectionBuffers, frustum, translationDelta)
        } else {
            SectionDrawList.buildAll(sectionBuffers)
        }
        val cullDoneNs = if (DEBUG_LOG) System.nanoTime() else 0L
        if (drawList.isEmpty()) {
            val result = if (useSectionFrustumCulling) {
                ChunkedDrawResult.NO_VISIBLE_SECTIONS
            } else {
                ChunkedDrawResult.NO_BUFFERS
            }
            if (DEBUG_LOG) {
                logDrawStats(sectionBuffers.size, 0, false, result, frameStartNs, cullDoneNs, cullDoneNs, cullDoneNs)
            }
            return result
        }

        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val a = (alpha and 0xFF) / 255f
        val colorTint = Vector4f(r, g, b, a)
        val deltaX = translationDelta.x
        val deltaY = translationDelta.y
        val deltaZ = translationDelta.z
        val camX = cameraPos.x
        val camY = cameraPos.y
        val camZ = cameraPos.z
        val dynamicUniforms = RenderSystem.getDynamicUniforms()
        val uniformSlices = ArrayList<GpuBufferSlice>(drawList.size)
        for (entry in drawList) {
            val mvMatrix = Matrix4f(baseMv)
            if (DEBUG_FORCE_FIXED_TRANSFORM) {
                mvMatrix.identity()
                mvMatrix.translate(0f, 0f, -8f)
            } else {
                val translation = PreviewSectionTransform.cameraRelative(
                    entry.sectionOriginX,
                    entry.sectionOriginY,
                    entry.sectionOriginZ,
                    camX,
                    camY,
                    camZ,
                    deltaX,
                    deltaY,
                    deltaZ,
                )
                mvMatrix.translate(translation.x, translation.y, translation.z)
            }
            uniformSlices += VersionCompatImpl.writeDynamicUniforms(
                dynamicUniforms,
                mvMatrix,
                colorTint,
                ZERO_VEC3,
                normalMatrix,
                1.0f,
            )
        }
        val uniformsDoneNs = if (DEBUG_LOG) System.nanoTime() else 0L

        val encoder = device.createCommandEncoder()
        val depthClear = if (DEBUG_CLEAR_DEPTH) OptionalDouble.of(1.0) else OptionalDouble.empty()
        val pass = encoder.createRenderPass(
            DEBUG_LABEL,
            colorView,
            OptionalInt.empty(),
            depthView,
            depthClear,
        )
        try {
            val renderLayer = RenderLayerCompat.blockTranslucentCull()
            val pipeline = if (USE_CUSTOM_PREVIEW_PIPELINE) {
                val firstBuffer = drawList.first().buffer
                VersionCompatImpl.getPreviewShellPipeline(firstBuffer.vertexFormatValue, firstBuffer.drawModeValue)
            } else {
                VersionCompatImpl.getRenderPipeline(renderLayer)
            }
            pass.setPipeline(pipeline)
            RenderSystem.bindDefaultUniforms(pass)

            val atlasView = VersionCompatImpl.getBlockAtlasTextureView(client)
            if (atlasView != null) {
                VersionCompatImpl.bindTextureToRenderPass(pass, "Sampler0", atlasView)
            }

            val lightmap = client.gameRenderer?.lightmapTextureManager
            val lightmapView = lightmap?.getGlTextureView()
            if (lightmapView != null) {
                VersionCompatImpl.bindTextureToRenderPass(pass, "Sampler2", lightmapView)
            }

            val batched = USE_MULTI_DRAW &&
                VersionCompatImpl.drawMultipleIndexedPreview(pass, drawList, uniformSlices)
            if (!batched) {
                for (i in drawList.indices) {
                    pass.setUniform("DynamicTransforms", uniformSlices[i])
                    drawList[i].buffer.drawIndexed(pass)
                }
            }
            if (DEBUG_LOG) {
                logDrawStats(
                    sectionBuffers.size,
                    drawList.size,
                    batched,
                    ChunkedDrawResult.DREW,
                    frameStartNs,
                    cullDoneNs,
                    uniformsDoneNs,
                    System.nanoTime(),
                )
            }
            return ChunkedDrawResult.DREW
        } finally {
            pass.close()
        }
    }

    private fun logDrawStats(
        totalSections: Int,
        visibleSections: Int,
        batched: Boolean,
        result: ChunkedDrawResult,
        frameStartNs: Long,
        cullDoneNs: Long,
        uniformsDoneNs: Long,
        submitDoneNs: Long,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastLogTime < LOG_INTERVAL_MS) return
        lastLogTime = now
        logger.info(
            "[Axion diag 1.21.7] drawChunked: sections={} visible={} culling={} batched={} result={} timesMs(cull={}, uniforms={}, submit={}, total={})",
            totalSections,
            visibleSections,
            USE_SECTION_FRUSTUM_CULLING,
            batched,
            result,
            millis(cullDoneNs - frameStartNs),
            millis(uniformsDoneNs - cullDoneNs),
            millis(submitDoneNs - uniformsDoneNs),
            millis(submitDoneNs - frameStartNs),
        )
    }

    private fun millis(ns: Long): String = "%.3f".format(ns / 1_000_000.0)

    private val ZERO_VEC3 = Vector3f(0f, 0f, 0f)
    private val DEBUG_LABEL: Supplier<String> = Supplier { "Axion preview" }
}

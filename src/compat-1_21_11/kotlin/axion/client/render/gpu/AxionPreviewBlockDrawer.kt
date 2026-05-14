package axion.client.render.gpu
import axion.client.compat.CameraAccess

import axion.client.render.AxionPreviewBuffer
import axion.client.render.RenderLayerCompat
import axion.client.compat.VersionCompatImpl
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.Frustum
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.slf4j.LoggerFactory
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.function.Supplier
import org.joml.Matrix4fc

/**
 * GPU-side draw orchestrator for cached preview chunks.
 *
 * Mirrors vanilla terrain rendering's per-chunk pattern: each section's
 * vertices are tessellated at SECTION-LOCAL coordinates (0..16 inside the
 * section's bounding box), and the matrix uniform is updated per section
 * to translate by `(sectionWorldOrigin - cameraPos)`. The current model-view
 * stack (which has the camera rotation applied at this point in MC's frame)
 * is the base; we only add the per-section translation.
 *
 * Per-frame work: 1 render pass + N×(setUniform + drawIndexed). No
 * tessellation, no upload.
 *
 * Uniforms wired:
 * Pipeline and vertex format come from [RenderLayerCompat.blockTranslucentCull],
 * matching the layer used when tessellating the cached [BuiltBuffer].
 *
 * Uniforms wired:
 *   - `Projection`           — `RenderSystem.getProjectionMatrixBuffer()`
 *   - `DynamicTransforms`    — written PER SECTION via [DynamicUniforms.write]
 *   - `Globals`              — `RenderSystem.getGlobalSettingsUniform()`
 *   - `Fog`                  — `RenderSystem.getShaderFog()`
 *   - `Sampler0` (atlas)     — block-atlas texture view + sampler
 *   - `Sampler2` (lightmap)  — lightmap view + atlas sampler
 *
 * Defensive: any exception is caught, logged once. After [MAX_FAILURES]
 * consecutive failures the drawer self-disables so Axion falls back to the
 * legacy CPU path.
 */
object AxionPreviewBlockDrawer {
    private val logger = LoggerFactory.getLogger(AxionPreviewBlockDrawer::class.java)
    private const val DEBUG_LOG: Boolean = false
    private const val LOG_INTERVAL_MS: Long = 1000
    private var lastLogTime: Long = 0

    /**
     * Gate for the experimental drawMultipleIndexed submission path.
     * When true, pre-computed uniform slices are submitted via
     * [VersionCompatImpl.drawMultipleIndexedPreview] (1.21.11 only;
     * 1.21.7 falls back to manual). When false, the manual per-section
     * setUniform + drawIndexed loop is used unconditionally.
     */
    private const val USE_MULTI_DRAW: Boolean = true
    private const val USE_CUSTOM_PREVIEW_PIPELINE: Boolean = true

    /**
     * Cull cached preview sections against the same deferred world-render
     * projection used to submit the draw. This is especially important for
     * large previews: looking away should produce an empty draw list instead
     * of still submitting every cached section.
     */
    private const val USE_SECTION_FRUSTUM_CULLING: Boolean = false

    /**
     * Force all sections to draw at a fixed camera-relative offset (0, 0, -8)
     * instead of their real world position. If this makes geometry visible,
     * the render pass setup is correct and the per-section transform is wrong.
     */
    private const val DEBUG_FORCE_FIXED_TRANSFORM: Boolean = false

    /**
     * Clear the depth buffer at the start of our render pass (depth = 1.0).
     * If this makes geometry visible, the preview is being hidden by existing
     * world depth — likely a compositing-order issue.
     */
    private const val DEBUG_CLEAR_DEPTH: Boolean = false

    private const val MAX_FAILURES: Int = 3
    private var failureCount: Int = 0
    private var disabled: Boolean = false

    /** One-shot diagnostic so users can confirm the GPU path is firing. */
    private var loggedFirstSuccess: Boolean = false

    fun isDisabled(): Boolean = disabled

    fun resetFailureState() {
        failureCount = 0
        disabled = false
        loggedFirstSuccess = false
    }

    /**
     * Draw all cached section buffers in one render pass with per-section
     * matrix translation. The provided map's keys are encoded section
     * coords (via [ChunkedBooleanStore.sectionKey]); values are uploaded
     * GPU buffers whose vertices are at section-local coords.
     */
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
            // Flush any deferred draws immediately after drawing
            ChunkedPreviewLifecycle.flushDeferredDraws(cullingModelView, projectionMatrix)
            if (result == ChunkedDrawResult.DREW && !loggedFirstSuccess) {
                loggedFirstSuccess = true
                logger.info(
                    "[Axion GPU] Preview drawer active — drew {} chunks via blockTranslucentCull pipeline.",
                    sectionBuffers.size,
                )
            }
            result
        } catch (t: Throwable) {
            failureCount++
            if (failureCount <= MAX_FAILURES) {
                logger.warn(
                    "[Axion GPU] Preview draw failed (attempt {} of {}). Falling back to CPU path for this frame.",
                    failureCount, MAX_FAILURES, t,
                )
            }
            if (failureCount >= MAX_FAILURES && !disabled) {
                disabled = true
                logger.warn(
                    "[Axion GPU] Disabling preview drawer after {} failures — all subsequent previews use the legacy CPU path.",
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

        // --- Phase 1: build the draw list ----------------------------------
        val useSectionFrustumCulling = USE_SECTION_FRUSTUM_CULLING
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

        // --- Phase 2: pre-compute per-section uniform data ------------------
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
                mvMatrix.translate(
                    (entry.sectionOriginX.toDouble() - camX + deltaX.toDouble()).toFloat(),
                    (entry.sectionOriginY.toDouble() - camY + deltaY.toDouble()).toFloat(),
                    (entry.sectionOriginZ.toDouble() - camZ + deltaZ.toDouble()).toFloat(),
                )
            }
            uniformSlices += VersionCompatImpl.writeDynamicUniforms(
                dynamicUniforms, mvMatrix, colorTint, ZERO_VEC3, normalMatrix, 1.0f,
            )
        }
        val uniformsDoneNs = if (DEBUG_LOG) System.nanoTime() else 0L

        // --- Phase 3: submit draw calls -------------------------------------
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
                    ?: VersionCompatImpl.getRenderPipeline(renderLayer)
            } else {
                VersionCompatImpl.getRenderPipeline(renderLayer)
            } ?: return ChunkedDrawResult.FAILED
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
            "[Axion diag] drawChunked: sections={} visible={} culling={} batched={} result={} timesMs(cull={}, uniforms={}, submit={}, total={})",
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

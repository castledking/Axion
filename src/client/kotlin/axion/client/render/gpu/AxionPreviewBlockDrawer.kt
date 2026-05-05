package axion.client.render.gpu

import axion.client.render.AxionPreviewBuffer
import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.objects.ObjectIterator
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.slf4j.LoggerFactory
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.function.Supplier

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
 *   - `Projection`           — `RenderSystem.getProjectionMatrixBuffer()`
 *   - `DynamicTransforms`    — written PER SECTION via [DynamicUniforms.write]
 *   - `Globals`              — `RenderSystem.getGlobalSettingsUniform()`
 *   - `Sampler0` (atlas)     — block-atlas texture view + sampler
 *   - `Sampler2` (lightmap)  — lightmap view + atlas sampler
 *
 * Defensive: any exception is caught, logged once. After [MAX_FAILURES]
 * consecutive failures the drawer self-disables so Axion falls back to the
 * legacy CPU path.
 */
object AxionPreviewBlockDrawer {
    private val logger = LoggerFactory.getLogger(AxionPreviewBlockDrawer::class.java)

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
    ) {
        if (disabled || sectionBuffers.isEmpty()) return

        try {
            doDrawChunked(sectionBuffers, color, alpha, translationDelta)
            if (!loggedFirstSuccess) {
                loggedFirstSuccess = true
                logger.info(
                    "[Axion GPU] Preview drawer active — drew {} chunks via RenderPipelines.TRANSLUCENT.",
                    sectionBuffers.size,
                )
            }
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
        }
    }

    private fun doDrawChunked(
        sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>,
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
    ) {
        val client = MinecraftClient.getInstance()
        val device = RenderSystem.getDevice()
        val mainTarget = client.framebuffer ?: return
        val colorView = mainTarget.colorAttachmentView ?: return
        val depthView = mainTarget.depthAttachmentView ?: return

        val camera = client.gameRenderer?.camera ?: return
        val cameraPos = camera.cameraPos ?: return

        // Color tint applied via the DynamicTransforms `colorTint` slot.
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val a = (alpha and 0xFF) / 255f
        val colorTint = Vector4f(r, g, b, a)

        // Base model-view matrix = current frame's matrix (camera rotation
        // already applied at this hook point). Per-section we'll add a
        // translate(sectionOrigin - cameraPos) on top.
        val baseMv = Matrix4f(RenderSystem.getModelViewMatrix())

        // The per-section translation we add on top of [baseMv] only affects
        // the last column of [mvMatrix]; the upper-3x3 (i.e. the camera
        // rotation) is identical across every section. The shader's normal
        // matrix only depends on that upper-3x3, so we can compute it ONCE
        // up front instead of doing a 4x4 invert+transpose per section.
        val normalMatrix = Matrix4f(baseMv).invert().transpose()

        // Frustum planes for cheap section-AABB culling. Extracted from
        // `proj * baseMv` once per frame; section centers offset by
        // [translationDelta] so they match the world position the GPU will
        // actually draw at after the per-section translate.
        val proj = client.gameRenderer?.getBasicProjectionMatrix(1.0f) ?: Matrix4f()
        val viewProj = Matrix4f(proj).mul(baseMv)
        val frustum = FrustumPlanes.fromViewProj(viewProj, cameraPos.x, cameraPos.y, cameraPos.z)
        val deltaX = translationDelta.x
        val deltaY = translationDelta.y
        val deltaZ = translationDelta.z

        val encoder = device.createCommandEncoder()
        val pass = encoder.createRenderPass(
            DEBUG_LABEL,
            colorView,
            OptionalInt.empty(),
            depthView,
            OptionalDouble.empty(),
        )
        try {
            pass.setPipeline(RenderPipelines.TRANSLUCENT)
            pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer())
            pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform())

            // Block atlas → Sampler0 (1.21.11 atlas-id format)
            val atlas = runCatching {
                client.atlasManager?.getAtlasTexture(BLOCK_ATLAS_DEFINITION_ID)
            }.getOrNull()
            val atlasView = atlas?.glTextureView
            val atlasSampler = atlas?.sampler
            if (atlasView != null && atlasSampler != null) {
                pass.bindTexture("Sampler0", atlasView, atlasSampler)
            }

            // Lightmap → Sampler2
            val lightmap = client.gameRenderer?.lightmapTextureManager
            val lightmapView = lightmap?.glTextureView
            if (lightmapView != null && atlasSampler != null) {
                pass.bindTexture("Sampler2", lightmapView, atlasSampler)
            }

            // Iterate sections; per-section update DynamicTransforms with the
            // section-specific translation, then drawIndexed that section.
            val dynamicUniforms = RenderSystem.getDynamicUniforms()
            val iter: ObjectIterator<Long2ObjectMap.Entry<AxionPreviewBuffer>> =
                sectionBuffers.long2ObjectEntrySet().iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val buf = entry.value
                if (!buf.isUploaded || buf.indexCountValue <= 0) continue

                val sectionKey = entry.longKey
                val sectionX = ChunkedBooleanStore.sectionX(sectionKey) shl 4
                val sectionY = ChunkedBooleanStore.sectionY(sectionKey) shl 4
                val sectionZ = ChunkedBooleanStore.sectionZ(sectionKey) shl 4

                // Frustum cull: skip sections whose 16³ AABB is fully outside
                // the camera view at its actual on-screen position.
                if (!frustum.isAabbVisible(
                        (sectionX + deltaX).toFloat(),
                        (sectionY + deltaY).toFloat(),
                        (sectionZ + deltaZ).toFloat(),
                        (sectionX + deltaX + 16).toFloat(),
                        (sectionY + deltaY + 16).toFloat(),
                        (sectionZ + deltaZ + 16).toFloat(),
                    )
                ) {
                    continue
                }

                val mvMatrix = Matrix4f(baseMv)
                mvMatrix.translate(
                    sectionX.toFloat() - cameraPos.x.toFloat() + deltaX.toFloat(),
                    sectionY.toFloat() - cameraPos.y.toFloat() + deltaY.toFloat(),
                    sectionZ.toFloat() - cameraPos.z.toFloat() + deltaZ.toFloat(),
                )
                val transforms = dynamicUniforms.write(mvMatrix, colorTint, ZERO_VEC3, normalMatrix)
                pass.setUniform("DynamicTransforms", transforms)
                buf.drawIndexed(pass)
            }
        } finally {
            pass.close()
        }
    }

    private val ZERO_VEC3 = Vector3f(0f, 0f, 0f)
    private val DEBUG_LABEL: Supplier<String> = Supplier { "Axion preview" }

    /**
     * Definition id of the block atlas in 1.21.11+. This lives at
     * `assets/minecraft/atlases/blocks.json` and is what
     * [net.minecraft.client.texture.AtlasManager.getAtlasTexture] expects.
     */
    private val BLOCK_ATLAS_DEFINITION_ID: Identifier = Identifier.of("minecraft", "blocks")
}

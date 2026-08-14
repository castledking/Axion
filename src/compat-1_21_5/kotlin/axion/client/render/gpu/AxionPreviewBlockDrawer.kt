package axion.client.render.gpu

import axion.client.compat.CameraAccess
import axion.client.render.AxionPreviewBuffer
import axion.client.render.RenderLayerCompat
import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.slf4j.LoggerFactory
import java.util.OptionalDouble
import java.util.OptionalInt

object AxionPreviewBlockDrawer {
    private val logger = LoggerFactory.getLogger(AxionPreviewBlockDrawer::class.java)
    private const val DEBUG_LOG: Boolean = false
    private const val LOG_INTERVAL_MS: Long = 1000
    private var lastLogTime: Long = 0
    private const val MAX_FAILURES: Int = 3
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

        return try {
            val result = doDrawChunked(
                sectionBuffers, color, alpha, translationDelta,
                baseModelView, cameraPosOverride,
            )
            if (result == ChunkedDrawResult.DREW && !loggedFirstSuccess) {
                loggedFirstSuccess = true
                logger.info(
                    "[Axion GPU 1.21.5] Preview drawer active — drew {} chunks via RENDERTYPE_TRANSLUCENT_MOVING_BLOCK.",
                    sectionBuffers.size,
                )
            }
            result
        } catch (t: Throwable) {
            failureCount++
            if (failureCount <= MAX_FAILURES) {
                logger.warn(
                    "[Axion GPU 1.21.5] Preview draw failed (attempt {} of {}). Falling back to CPU path.",
                    failureCount, MAX_FAILURES, t,
                )
            }
            if (failureCount >= MAX_FAILURES && !disabled) {
                disabled = true
                logger.warn(
                    "[Axion GPU 1.21.5] Disabling preview drawer after {} failures — using legacy CPU path.",
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
    ): ChunkedDrawResult {
        val client = MinecraftClient.getInstance()
        val device = RenderSystem.getDevice()
        val mainTarget = client.framebuffer ?: return ChunkedDrawResult.FAILED
        val colorAttachment = mainTarget.colorAttachment ?: return ChunkedDrawResult.FAILED
        val depthAttachment = mainTarget.depthAttachment ?: return ChunkedDrawResult.FAILED

        val camera = client.gameRenderer?.camera ?: return ChunkedDrawResult.FAILED
        val cameraPos = cameraPosOverride ?: CameraAccess.getPos(camera)

        // Fabric's 1.21.5 world-render MatrixStack is identity at this event.
        // The direct render pass must explicitly use the active camera
        // rotation that buffered RenderLayers otherwise apply during flush.
        val baseMv = Matrix4f(RenderSystem.getModelViewMatrix())

        val drawList = SectionDrawList.buildAll(sectionBuffers)
        if (drawList.isEmpty()) return ChunkedDrawResult.NO_BUFFERS

        // The 1.21.5 backend cannot upload/grow buffers while a pass is open.
        // Pre-warm shared sequential indices for every section before creating
        // the direct preview pass.
        drawList.forEach { it.buffer.prepareSequentialIndexBuffer() }

        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val a = (alpha and 0xFF) / 255f
        val deltaX = translationDelta.x
        val deltaY = translationDelta.y
        val deltaZ = translationDelta.z
        val camX = cameraPos.x
        val camY = cameraPos.y
        val camZ = cameraPos.z

        // 1.21.5's GL backend ignores RenderPass.setUniform for anything in
        // ShaderProgram.PREDEFINED_UNIFORMS. GlResourceManager.setupRenderPass
        // applies the pass-local values first, then calls
        // ShaderProgram.initializeUniforms(mode, RenderSystem.getModelViewMatrix(),
        // RenderSystem.getProjectionMatrix(), ...) which overwrites ModelViewMat,
        // ProjMat and ColorModulator from the RenderSystem globals before the
        // uniforms are uploaded. Setting them on the pass silently collapses every
        // section onto the same origin with an untinted colour, so the per-section
        // transform and the preview tint are published through RenderSystem here.
        // 1.21.6+ has DynamicTransforms/bindDefaultUniforms and does not need this.
        val modelViewStack = RenderSystem.getModelViewStack()
        val previousColor = RenderSystem.getShaderColor().copyOf()

        modelViewStack.pushMatrix()
        RenderSystem.setShaderColor(r, g, b, a)
        try {
            val encoder = device.createCommandEncoder()
            val pass = encoder.createRenderPass(
                colorAttachment,
                OptionalInt.empty(),
                depthAttachment,
                OptionalDouble.empty(),
            )
            try {
                pass.setPipeline(RenderPipelines.RENDERTYPE_TRANSLUCENT_MOVING_BLOCK)

                val atlas = client.bakedModelManager
                    ?.getAtlas(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                    ?.glTexture
                if (atlas != null) {
                    pass.bindSampler("Sampler0", atlas)
                }

                val lightmapTexture = client.gameRenderer?.lightmapTextureManager?.glTexture
                if (lightmapTexture != null) {
                    pass.bindSampler("Sampler2", lightmapTexture)
                }

                for (entry in drawList) {
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
                    // initializeUniforms reads the stack on every draw call, so the
                    // section transform has to be live on it before drawIndexed.
                    modelViewStack.set(baseMv)
                    modelViewStack.translate(translation.x, translation.y, translation.z)
                    entry.buffer.drawIndexed(pass)
                }

                if (DEBUG_LOG) {
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime >= LOG_INTERVAL_MS) {
                        lastLogTime = now
                        logger.info(
                            "[Axion GPU 1.21.5] drawChunked: sections={} visible={}",
                            sectionBuffers.size, drawList.size,
                        )
                    }
                }
                return ChunkedDrawResult.DREW
            } finally {
                pass.close()
            }
        } finally {
            modelViewStack.popMatrix()
            RenderSystem.setShaderColor(
                previousColor[0],
                previousColor[1],
                previousColor[2],
                previousColor[3],
            )
        }
    }
}

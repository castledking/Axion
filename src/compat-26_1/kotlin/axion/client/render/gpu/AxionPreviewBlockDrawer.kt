package axion.client.render.gpu

import axion.client.compat.CameraAccess
import axion.client.compat.VersionCompatImpl
import axion.client.render.AxionPreviewBuffer
import axion.client.render.RenderLayerCompat
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.client.MinecraftClient
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

object AxionPreviewBlockDrawer {
    private val logger = LoggerFactory.getLogger(AxionPreviewBlockDrawer::class.java)
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
            val result = doDrawChunked(sectionBuffers, color, alpha, translationDelta, baseModelView, cameraPosOverride)
            if (result == ChunkedDrawResult.DREW && !loggedFirstSuccess) {
                loggedFirstSuccess = true
                logger.info("[Axion GPU] 26.1.x preview drawer active; drew {} section buffers.", sectionBuffers.size)
            }
            result
        } catch (t: Throwable) {
            failureCount++
            if (failureCount <= MAX_FAILURES) {
                logger.warn(
                    "[Axion GPU] 26.1.x preview draw failed (attempt {} of {}).",
                    failureCount,
                    MAX_FAILURES,
                    t,
                )
            }
            if (failureCount >= MAX_FAILURES && !disabled) {
                disabled = true
                logger.warn("[Axion GPU] Disabling 26.1.x preview drawer after {} failures.", MAX_FAILURES)
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
        val colorView = mainTarget.colorTextureView ?: return ChunkedDrawResult.FAILED
        val depthView = mainTarget.depthTextureView ?: return ChunkedDrawResult.FAILED
        val camera = client.gameRenderer?.camera ?: return ChunkedDrawResult.FAILED
        val cameraPos = cameraPosOverride ?: CameraAccess.getPos(camera)
        val baseMv = Matrix4f(RenderSystem.getModelViewMatrix())
        val normalMatrix = Matrix4f(baseMv).invert().transpose()
        val drawList = SectionDrawList.buildAll(sectionBuffers)
        if (drawList.isEmpty()) return ChunkedDrawResult.NO_BUFFERS

        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val a = (alpha and 0xFF) / 255f
        val colorTint = Vector4f(r, g, b, a)
        val dynamicUniforms = RenderSystem.getDynamicUniforms()
        val uniformSlices = ArrayList<GpuBufferSlice>(drawList.size)
        for (entry in drawList) {
            val mvMatrix = Matrix4f(baseMv)
            mvMatrix.translate(
                (entry.sectionOriginX.toDouble() - cameraPos.x + translationDelta.x.toDouble()).toFloat(),
                (entry.sectionOriginY.toDouble() - cameraPos.y + translationDelta.y.toDouble()).toFloat(),
                (entry.sectionOriginZ.toDouble() - cameraPos.z + translationDelta.z.toDouble()).toFloat(),
            )
            uniformSlices += VersionCompatImpl.writeDynamicUniforms(
                dynamicUniforms,
                mvMatrix,
                colorTint,
                ZERO_VEC3,
                normalMatrix,
                1.0f,
            )
        }

        val encoder = device.createCommandEncoder()
        val pass = encoder.createRenderPass(
            DEBUG_LABEL,
            colorView,
            OptionalInt.empty(),
            depthView,
            OptionalDouble.empty(),
        )
        try {
            val renderLayer = RenderLayerCompat.translucentMovingBlock()
            pass.setPipeline(VersionCompatImpl.getRenderPipeline(renderLayer))
            RenderSystem.bindDefaultUniforms(pass)

            VersionCompatImpl.getBlockAtlasTextureView(client)?.let { atlasView ->
                VersionCompatImpl.bindTextureToRenderPass(pass, "Sampler0", atlasView)
            }
            client.gameRenderer?.levelLightmap()?.let { lightmapView ->
                VersionCompatImpl.bindTextureToRenderPass(pass, "Sampler2", lightmapView)
            }

            val batched = VersionCompatImpl.drawMultipleIndexedPreview(pass, drawList, uniformSlices)
            if (!batched) {
                for (i in drawList.indices) {
                    pass.setUniform("DynamicTransforms", uniformSlices[i])
                    drawList[i].buffer.drawIndexed(pass)
                }
            }
            return ChunkedDrawResult.DREW
        } finally {
            pass.close()
        }
    }

    private val ZERO_VEC3 = Vector3f(0f, 0f, 0f)
    private val DEBUG_LABEL: Supplier<String> = Supplier { "Axion 26.1.x preview" }
}

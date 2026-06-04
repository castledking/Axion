package axion.client.render.gpu

import axion.client.render.AxionPreviewBuffer
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4fc

object AxionPreviewBlockDrawer {
    fun isDisabled(): Boolean = true

    fun resetFailureState() = Unit

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
        return if (sectionBuffers.isEmpty()) ChunkedDrawResult.NO_BUFFERS else ChunkedDrawResult.FAILED
    }
}

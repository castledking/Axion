package axion.client.render.gpu

import axion.client.render.AxionPreviewBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.objects.ObjectIterator
import net.minecraft.client.render.Frustum
import net.minecraft.util.math.Vec3i
import net.minecraft.util.math.Box

/**
 * Per-section draw metadata collected after frustum culling.
 *
 * Stage 1: consumed by the per-section draw loop in [AxionPreviewBlockDrawer].
 * Stage 2 target: consumed by bulk uniform write + drawMultipleIndexed.
 */
class SectionDrawEntry(
    @JvmField val sectionKey: Long,
    @JvmField val sectionOriginX: Int,
    @JvmField val sectionOriginY: Int,
    @JvmField val sectionOriginZ: Int,
    @JvmField val buffer: AxionPreviewBuffer,
    @JvmField val indexCount: Int,
    @JvmField val indexType: VertexFormat.IndexType,
)

object SectionDrawList {
    /**
     * Build the list of every uploaded section draw entry with no frustum
     * rejection. This is the correctness-first path while section culling is
     * gated off.
     */
    fun buildAll(
        sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>,
    ): ArrayList<SectionDrawEntry> {
        val result = ArrayList<SectionDrawEntry>(sectionBuffers.size)
        val iter: ObjectIterator<Long2ObjectMap.Entry<AxionPreviewBuffer>> =
            sectionBuffers.long2ObjectEntrySet().iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val buf = entry.value
            if (!buf.isUploaded || buf.indexCountValue <= 0) continue

            val sectionKey = entry.longKey
            val originX = ChunkedBooleanStore.sectionX(sectionKey) shl 4
            val originY = ChunkedBooleanStore.sectionY(sectionKey) shl 4
            val originZ = ChunkedBooleanStore.sectionZ(sectionKey) shl 4
            result += SectionDrawEntry(
                sectionKey, originX, originY, originZ,
                buf, buf.indexCountValue, buf.indexTypeValue,
            )
        }
        return result
    }

    /**
     * Build the list of visible section draw entries by iterating all cached
     * section buffers and rejecting those outside the camera frustum.
     *
     * The returned list owns no resources — entries hold refs to the existing
     * [AxionPreviewBuffer] objects and are only valid for the current frame.
     */
    fun buildVisible(
        sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>,
        frustum: Frustum,
        translationDelta: Vec3i,
    ): ArrayList<SectionDrawEntry> {
        val result = ArrayList<SectionDrawEntry>(sectionBuffers.size)
        val deltaX = translationDelta.x
        val deltaY = translationDelta.y
        val deltaZ = translationDelta.z

        val iter: ObjectIterator<Long2ObjectMap.Entry<AxionPreviewBuffer>> =
            sectionBuffers.long2ObjectEntrySet().iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val buf = entry.value
            if (!buf.isUploaded || buf.indexCountValue <= 0) continue

            val sectionKey = entry.longKey
            val originX = ChunkedBooleanStore.sectionX(sectionKey) shl 4
            val originY = ChunkedBooleanStore.sectionY(sectionKey) shl 4
            val originZ = ChunkedBooleanStore.sectionZ(sectionKey) shl 4

            val minX = originX + deltaX
            val minY = originY + deltaY
            val minZ = originZ + deltaZ
            if (!frustum.isVisible(Box(
                    minX.toDouble(),
                    minY.toDouble(),
                    minZ.toDouble(),
                    (minX + 16).toDouble(),
                    (minY + 16).toDouble(),
                    (minZ + 16).toDouble(),
                ))
            ) continue

            result += SectionDrawEntry(
                sectionKey, originX, originY, originZ,
                buf, buf.indexCountValue, buf.indexTypeValue,
            )
        }
        return result
    }
}

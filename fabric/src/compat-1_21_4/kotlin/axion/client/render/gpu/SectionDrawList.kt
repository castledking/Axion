package axion.client.render.gpu

import axion.client.render.AxionPreviewBuffer
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.objects.ObjectIterator
import net.minecraft.client.render.Frustum
import net.minecraft.util.math.Vec3i
import net.minecraft.util.math.Box

class SectionDrawEntry(
    @JvmField val sectionKey: Long,
    @JvmField val sectionOriginX: Int,
    @JvmField val sectionOriginY: Int,
    @JvmField val sectionOriginZ: Int,
    @JvmField val buffer: AxionPreviewBuffer,
    @JvmField val indexCount: Int,
)

object SectionDrawList {
    fun buildAll(sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>): ArrayList<SectionDrawEntry> {
        val result = ArrayList<SectionDrawEntry>(sectionBuffers.size)
        val iterator: ObjectIterator<Long2ObjectMap.Entry<AxionPreviewBuffer>> =
            sectionBuffers.long2ObjectEntrySet().iterator()
        while (iterator.hasNext()) {
            appendIfUploaded(result, iterator.next())
        }
        return result
    }

    fun buildVisible(
        sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>,
        frustum: Frustum,
        translationDelta: Vec3i,
    ): ArrayList<SectionDrawEntry> {
        val result = ArrayList<SectionDrawEntry>(sectionBuffers.size)
        val iterator: ObjectIterator<Long2ObjectMap.Entry<AxionPreviewBuffer>> =
            sectionBuffers.long2ObjectEntrySet().iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val sectionKey = entry.longKey
            val originX = ChunkedBooleanStore.sectionX(sectionKey) shl 4
            val originY = ChunkedBooleanStore.sectionY(sectionKey) shl 4
            val originZ = ChunkedBooleanStore.sectionZ(sectionKey) shl 4
            val minX = originX + translationDelta.x
            val minY = originY + translationDelta.y
            val minZ = originZ + translationDelta.z
            if (!frustum.isVisible(Box(
                    minX.toDouble(), minY.toDouble(), minZ.toDouble(),
                    (minX + 16).toDouble(), (minY + 16).toDouble(), (minZ + 16).toDouble(),
                ))) continue
            appendIfUploaded(result, entry)
        }
        return result
    }

    private fun appendIfUploaded(
        result: MutableList<SectionDrawEntry>,
        entry: Long2ObjectMap.Entry<AxionPreviewBuffer>,
    ) {
        val buffer = entry.value
        if (!buffer.isUploaded || buffer.indexCountValue <= 0) return
        val sectionKey = entry.longKey
        result += SectionDrawEntry(
            sectionKey = sectionKey,
            sectionOriginX = ChunkedBooleanStore.sectionX(sectionKey) shl 4,
            sectionOriginY = ChunkedBooleanStore.sectionY(sectionKey) shl 4,
            sectionOriginZ = ChunkedBooleanStore.sectionZ(sectionKey) shl 4,
            buffer = buffer,
            indexCount = buffer.indexCountValue,
        )
    }
}

package axion.client.render.gpu

import axion.client.render.AxionPreviewBuffer
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.client.render.Frustum
import net.minecraft.util.math.Vec3i

class SectionDrawEntry

object SectionDrawList {
    fun buildAll(sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>): ArrayList<SectionDrawEntry> = ArrayList()

    fun buildVisible(
        sectionBuffers: Long2ObjectMap<AxionPreviewBuffer>,
        frustum: Frustum,
        translationDelta: Vec3i,
    ): ArrayList<SectionDrawEntry> = ArrayList()
}

package axion.client.tool

import axion.common.model.ClipboardBuffer
import axion.protocol.EntitySelectionMask
import axion.protocol.IntVector3
import net.minecraft.util.math.Vec3i

internal fun ClipboardBuffer.toEntitySelectionMask(): EntitySelectionMask {
    val volume = size.x.toLong() * size.y.toLong() * size.z.toLong()
    if (volume > 0L && cells.size.toLong() == volume) {
        return EntitySelectionMask.fullRegion()
    }
    return EntitySelectionMask.fromSelectedOffsets(
        sourceSize = size.toProtocolVector(),
        selectedOffsets = cells.asSequence().map { it.offset.toProtocolVector() }.asIterable(),
    )
}

internal fun EntitySelectionMask.selectedOffsets(size: Vec3i): Sequence<Vec3i> =
    selectedOffsets(size.toProtocolVector()).map { Vec3i(it.x, it.y, it.z) }

internal fun Vec3i.toProtocolVector(): IntVector3 = IntVector3(x, y, z)

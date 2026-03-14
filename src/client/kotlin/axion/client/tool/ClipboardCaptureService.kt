package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import axion.client.network.BlockEntitySnapshotService
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import net.minecraft.world.World

object ClipboardCaptureService {
    fun capture(world: World, region: BlockRegion): ClipboardBuffer {
        val normalized = region.normalized()
        val min = normalized.minCorner()
        val max = normalized.maxCorner()
        val cells = buildList {
            for (pos in BlockPos.iterate(min, max)) {
                add(
                    ClipboardCell(
                        offset = Vec3i(pos.x - min.x, pos.y - min.y, pos.z - min.z),
                        state = world.getBlockState(pos),
                        blockEntityData = BlockEntitySnapshotService.capture(world, pos),
                    ),
                )
            }
        }

        return ClipboardBuffer(size = normalized.size(), cells = cells)
    }
}

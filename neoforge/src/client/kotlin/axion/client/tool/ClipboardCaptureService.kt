package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import axion.client.network.BlockEntitySnapshotService
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import axion.client.compat.blockPosIterate
import net.minecraft.world.level.Level

object ClipboardCaptureService {
    fun capture(world: Level, region: BlockRegion): ClipboardBuffer {
        val normalized = region.normalized()
        val min = normalized.minCorner()
        val max = normalized.maxCorner()
        val cells = buildList {
            for (pos in blockPosIterate(min, max)) {
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

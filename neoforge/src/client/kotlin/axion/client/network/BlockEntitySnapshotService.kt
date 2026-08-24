package axion.client.network

import axion.common.model.BlockEntityDataSnapshot
import axion.client.compat.VersionCompatImpl
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

object BlockEntitySnapshotService {
    fun capture(world: Level, pos: BlockPos): BlockEntityDataSnapshot? = VersionCompatImpl.captureBlockEntity(world, pos)

    fun apply(world: Level, write: BlockWrite, suppressUpdates: Boolean = true) =
        VersionCompatImpl.applyBlockEntity(world, write, suppressUpdates)
}

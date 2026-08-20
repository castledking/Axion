package axion.client.network

import axion.common.model.BlockEntityDataSnapshot
import axion.client.compat.VersionCompatImpl
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object BlockEntitySnapshotService {
    fun capture(world: World, pos: BlockPos): BlockEntityDataSnapshot? = VersionCompatImpl.captureBlockEntity(world, pos)

    fun apply(world: World, write: BlockWrite, suppressUpdates: Boolean = true) =
        VersionCompatImpl.applyBlockEntity(world, write, suppressUpdates)
}

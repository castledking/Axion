package axion.client.network

import axion.common.model.BlockEntityDataSnapshot
import net.minecraft.block.BlockEntityProvider
import net.minecraft.block.entity.BlockEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object BlockEntitySnapshotService {
    fun capture(world: World, pos: BlockPos): BlockEntityDataSnapshot? {
        val blockEntity = world.getBlockEntity(pos) ?: return null
        return BlockEntityDataSnapshot(
            blockEntity.createNbtWithIdentifyingData(world.registryManager).copy(),
        )
    }

    fun apply(world: World, write: BlockWrite) {
        world.setBlockState(write.pos, write.state, 3)

        val provider = write.state.block as? BlockEntityProvider
        val payload = write.blockEntityData
        if (payload == null) {
            if (provider == null) {
                world.removeBlockEntity(write.pos)
                return
            }

            val defaultBlockEntity = provider.createBlockEntity(write.pos, write.state) ?: return
            world.removeBlockEntity(write.pos)
            world.getWorldChunk(write.pos).setBlockEntity(defaultBlockEntity)
            defaultBlockEntity.markDirty()
            return
        }

        val restored = BlockEntity.createFromNbt(
            write.pos,
            write.state,
            payload.nbt.copy(),
            world.registryManager,
        ) ?: return

        world.removeBlockEntity(write.pos)
        world.getWorldChunk(write.pos).setBlockEntity(restored)
        restored.markDirty()
    }
}

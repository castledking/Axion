package axion.server.fabric

import net.minecraft.block.BlockEntityProvider
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.StringNbtReader
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

object AxionFabricBlockEntitySnapshotService {
    fun capture(world: ServerWorld, pos: BlockPos): String? {
        val blockEntity = world.getBlockEntity(pos) ?: return null
        return blockEntity.createNbtWithIdentifyingData(world.registryManager).toString()
    }

    fun apply(
        world: ServerWorld,
        pos: BlockPos,
        state: BlockState,
        blockEntityData: String?,
    ) {
        world.setBlockState(pos, state, 3)

        val provider = state.block as? BlockEntityProvider
        if (blockEntityData == null) {
            if (provider == null) {
                world.removeBlockEntity(pos)
                return
            }

            val defaultBlockEntity = provider.createBlockEntity(pos, state) ?: return
            world.removeBlockEntity(pos)
            world.getWorldChunk(pos).setBlockEntity(defaultBlockEntity)
            defaultBlockEntity.markDirty()
            return
        }

        val restored = BlockEntity.createFromNbt(
            pos,
            state,
            StringNbtReader.readCompound(blockEntityData),
            world.registryManager,
        ) ?: return

        world.removeBlockEntity(pos)
        world.getWorldChunk(pos).setBlockEntity(restored)
        restored.markDirty()
    }
}

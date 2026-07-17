package axion.server.fabric

import net.minecraft.block.BlockEntityProvider
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.StringNbtReader
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

object AxionFabricBlockEntitySnapshotService {
    // Notify clients, keep the written shape, suppress drops, and skip old/new block callbacks.
    // The callback bits are available on the dedicated-server target (Minecraft 1.21.11).
    private const val NO_PHYSICS_UPDATE_FLAGS = 2 or 16 or 32 or 256 or 512

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
        world.setBlockState(pos, state, NO_PHYSICS_UPDATE_FLAGS)

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

        val restoredNbt = StringNbtReader.readCompound(blockEntityData)
        restoredNbt.putInt("x", pos.x)
        restoredNbt.putInt("y", pos.y)
        restoredNbt.putInt("z", pos.z)

        val restored = BlockEntity.createFromNbt(
            pos,
            state,
            restoredNbt,
            world.registryManager,
        ) ?: return

        world.removeBlockEntity(pos)
        world.getWorldChunk(pos).setBlockEntity(restored)
        restored.markDirty()
    }
}

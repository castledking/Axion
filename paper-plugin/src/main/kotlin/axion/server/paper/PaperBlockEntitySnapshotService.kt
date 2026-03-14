package axion.server.paper

import net.minecraft.core.BlockPos
import net.minecraft.nbt.TagParser
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.storage.TagValueInput
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.craftbukkit.CraftWorld

object PaperBlockEntitySnapshotService {
    fun capture(world: World, pos: BlockPos): String? {
        val level = (world as CraftWorld).handle
        val blockEntity = level.getBlockEntity(pos) ?: return null
        return blockEntity.saveWithFullMetadata(level.registryAccess()).toString()
    }

    fun apply(world: World, pos: BlockPos, blockStateString: String, blockEntityPayload: String?) {
        val level = (world as CraftWorld).handle
        world.getBlockAt(pos.x, pos.y, pos.z).setBlockData(Bukkit.createBlockData(blockStateString), false)

        val blockState = level.getBlockState(pos)
        if (blockEntityPayload == null) {
            if (!blockState.hasBlockEntity()) {
                level.getChunkAt(pos).removeBlockEntity(pos)
            }
            return
        }

        val tag = TagParser.parseCompoundFully(blockEntityPayload)
        val existing = level.getBlockEntity(pos)
        if (existing != null) {
            existing.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag.copy()),
            )
            existing.setChanged()
            level.sendBlockUpdated(pos, blockState, blockState, 3)
            return
        }

        val restored = BlockEntity.loadStatic(pos, blockState, tag.copy(), level.registryAccess()) ?: return
        level.getChunkAt(pos).setBlockEntity(restored)
        restored.setChanged()
        level.sendBlockUpdated(pos, blockState, blockState, 3)
    }
}

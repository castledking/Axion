package axion.server.paper

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.craftbukkit.CraftWorld

object PaperBlockEntitySnapshotService {
    private val logger = java.util.logging.Logger.getLogger("Axion")
    fun capture(world: World, pos: BlockPos): String? {
        val level = (world as CraftWorld).handle
        val blockEntity = level.getBlockEntity(pos) ?: return null
        return blockEntity.saveWithFullMetadata(level.registryAccess()).toString()
    }

    fun apply(
        world: World,
        pos: BlockPos,
        blockStateString: String,
        blockEntityPayload: String?,
        suppressUpdates: Boolean = true,
    ) {
        val level = (world as CraftWorld).handle
        PaperBlockWritePolicy.setBlockData(
            world.getBlockAt(pos.x, pos.y, pos.z),
            Bukkit.createBlockData(blockStateString),
            applyPhysics = !suppressUpdates,
        )

        val blockState = level.getBlockState(pos)
        if (blockEntityPayload == null) {
            if (!blockState.hasBlockEntity()) {
                level.getChunkAt(pos).removeBlockEntity(pos)
            }
            return
        }

        val tag = try {
            rebase(PaperNbtCompat.parseCompound(blockEntityPayload), pos)
        } catch (e: Exception) {
            logger.warning("Failed to parse block entity NBT at ${pos.x},${pos.y},${pos.z}: ${e.message}")
            return
        }
        val existing = level.getBlockEntity(pos)
        if (existing != null) {
            runCatching { loadBlockEntityWithComponents(existing, tag.copy(), level.registryAccess()) }
                .onFailure { e ->
                    logger.warning("Failed to load block entity components at ${pos.x},${pos.y},${pos.z}: ${e.message}")
                }
                .getOrElse { return }
            existing.setChanged()
            level.sendBlockUpdated(pos, blockState, blockState, 3)
            return
        }

        val restored = runCatching {
            BlockEntity.loadStatic(pos, blockState, tag.copy(), level.registryAccess())
        }.onFailure { e ->
            logger.warning("Failed to restore block entity at ${pos.x},${pos.y},${pos.z}: ${e.message}")
        }.getOrNull() ?: return
        level.getChunkAt(pos).setBlockEntity(restored)
        restored.setChanged()
        level.sendBlockUpdated(pos, blockState, blockState, 3)
    }

    private fun rebase(tag: CompoundTag, pos: BlockPos): CompoundTag {
        tag.putInt("x", pos.x)
        tag.putInt("y", pos.y)
        tag.putInt("z", pos.z)
        return tag
    }

    private fun loadBlockEntityWithComponents(
        blockEntity: BlockEntity,
        tag: CompoundTag,
        registryAccess: net.minecraft.core.HolderLookup.Provider,
    ) {
        val compoundMethod = blockEntity.javaClass.methods.firstOrNull { method ->
            method.name == "loadWithComponents" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == CompoundTag::class.java
        }
        if (compoundMethod != null) {
            compoundMethod.invoke(blockEntity, tag, registryAccess)
            return
        }

        val inputClass = Class.forName("net.minecraft.world.level.storage.TagValueInput")
        val reporter = Class.forName("net.minecraft.util.ProblemReporter").getField("DISCARDING").get(null)
        val input = inputClass
            .getMethod("create", reporter.javaClass.interfaces.firstOrNull() ?: reporter.javaClass, registryAccess.javaClass.interfaces.firstOrNull() ?: registryAccess.javaClass, CompoundTag::class.java)
            .invoke(null, reporter, registryAccess, tag)
        blockEntity.javaClass.methods.first { method ->
            method.name == "loadWithComponents" && method.parameterTypes.size == 1
        }.invoke(blockEntity, input)
    }
}

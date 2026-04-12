package axion.client.compat

import axion.common.compat.VersionCompat
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.client.MinecraftClient

/**
 * Version compatibility implementation for Minecraft 1.20.4 - 1.20.6
 * Uses the pre-1.21 Registry system (Registries instead of BuiltInRegistries)
 */
object VersionCompatImpl : VersionCompat {
    
    private fun getRegistryManager(): DynamicRegistryManager? {
        return MinecraftClient.getInstance().world?.registryManager
    }

    override fun getBlock(id: Identifier): Block? {
        return Registries.BLOCK.get(id)
    }

    override fun getItem(id: Identifier): Item? {
        return Registries.ITEM.get(id)
    }

    override fun getBlockId(block: Block): Identifier {
        return Registries.BLOCK.getId(block)
    }

    override fun getItemId(item: Item): Identifier {
        return Registries.ITEM.getId(item)
    }

    override fun getAllBlocks(): Collection<Block> {
        return Registries.BLOCK
    }

    override fun getAllItems(): Collection<Item> {
        return Registries.ITEM
    }

    // In 1.20.6, Identifier constructor is still public
    override fun parseIdentifier(id: String): Identifier {
        return Identifier(id)
    }

    override fun identifierOf(namespace: String, path: String): Identifier {
        return Identifier(namespace, path)
    }

    // BlockState uses the registry-based parser in 1.20.6
    override fun blockStateToString(state: BlockState): String {
        return BlockArgumentParser.stringify(state)
    }

    override fun stringToBlockState(str: String): BlockState? {
        val registryManager = getRegistryManager() ?: return null
        return try {
            BlockArgumentParser.block(
                registryManager.getOrThrow(net.minecraft.registry.RegistryKeys.BLOCK),
                str,
                false
            ).blockState()
        } catch (e: Exception) {
            null
        }
    }

    // NBT serialization - uses encode/decode methods available in 1.20.6
    override fun itemStackToNbt(stack: ItemStack): NbtCompound {
        return stack.writeNbt(NbtCompound())
    }

    override fun nbtToItemStack(nbt: NbtCompound): ItemStack {
        return ItemStack.fromNbt(getRegistryManager()?.registryWrapperOrThrow(net.minecraft.registry.RegistryKeys.ITEM) 
            ?: return ItemStack.EMPTY, nbt)
    }

    override fun shouldUseNonConsumingKeybind(): Boolean {
        // 1.20.6 and earlier need non-consuming keybinds to handle conflicts
        return true
    }
}

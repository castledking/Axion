package axion.common.compat

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier

/**
 * Version compatibility abstraction layer.
 * Provides a common interface for operations that differ between Minecraft versions.
 */
interface VersionCompat {
    companion object {
        lateinit var INSTANCE: VersionCompat
            private set

        fun initialize(impl: VersionCompat) {
            INSTANCE = impl
        }
    }

    // Registry operations
    fun getBlock(id: Identifier): Block?
    fun getItem(id: Identifier): Item?
    fun getBlockId(block: Block): Identifier
    fun getItemId(item: Item): Identifier
    fun getAllBlocks(): Collection<Block>
    fun getAllItems(): Collection<Item>

    // ResourceLocation/Identifier operations  
    fun parseIdentifier(id: String): Identifier
    fun identifierOf(namespace: String, path: String): Identifier

    // BlockState operations
    fun blockStateToString(state: BlockState): String
    fun stringToBlockState(str: String): BlockState?

    // NBT serialization (differed between 1.20 and 1.21)
    fun itemStackToNbt(stack: ItemStack): NbtCompound
    fun nbtToItemStack(nbt: NbtCompound): ItemStack

    // Keybinding handling (1.21.7 and earlier need special handling for conflicting keys)
    fun shouldUseNonConsumingKeybind(): Boolean
}

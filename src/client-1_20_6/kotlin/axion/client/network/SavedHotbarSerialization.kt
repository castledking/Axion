package axion.client.network

import axion.client.compat.VersionCompatImpl
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.DynamicRegistryManager

/**
 * ItemStack serialization helper for 1.20.6
 * Uses PacketByteBuf instead of RegistryByteBuf
 */
object SavedHotbarSerialization {
    
    fun serializeStack(registryManager: DynamicRegistryManager, stack: ItemStack, buf: PacketByteBuf) {
        if (stack.isEmpty) {
            buf.writeBoolean(false)
            return
        }
        buf.writeBoolean(true)
        val nbt = VersionCompatImpl.itemStackToNbt(stack)
        buf.writeNbt(nbt)
    }

    fun deserializeStack(registryManager: DynamicRegistryManager, buf: PacketByteBuf): ItemStack {
        if (!buf.readBoolean()) {
            return ItemStack.EMPTY
        }
        val nbt = buf.readNbt() ?: return ItemStack.EMPTY
        return VersionCompatImpl.nbtToItemStack(nbt)
    }

    // Helper for encoding to Base64 (used in config)
    fun serializeStackToNbt(stack: ItemStack): NbtCompound {
        return VersionCompatImpl.itemStackToNbt(stack)
    }

    fun deserializeStackFromNbt(nbt: NbtCompound): ItemStack {
        return VersionCompatImpl.nbtToItemStack(nbt)
    }
}

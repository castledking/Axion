package axion.client.compat

import axion.common.compat.VersionCompat
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier

/**
 * Version compatibility implementation for Minecraft 1.21.11
 */
object VersionCompatImpl : VersionCompat {

    private fun getRegistryManager(): DynamicRegistryManager? {
        return MinecraftClient.getInstance().world?.registryManager
    }

    private fun registryManagerOrThrow(): DynamicRegistryManager {
        return getRegistryManager()
            ?: throw IllegalStateException("Registry manager not available")
    }

    override fun getBlock(id: Identifier): Block? {
        val block = Registries.BLOCK.get(id)
        return if (block == net.minecraft.block.Blocks.AIR && id != Registries.BLOCK.getId(net.minecraft.block.Blocks.AIR)) {
            null
        } else {
            block
        }
    }

    override fun getItem(id: Identifier): Item? {
        val item = Registries.ITEM.get(id)
        return if (item == net.minecraft.item.Items.AIR && id != Registries.ITEM.getId(net.minecraft.item.Items.AIR)) {
            null
        } else {
            item
        }
    }

    override fun getBlockId(block: Block): Identifier {
        return Registries.BLOCK.getId(block)
    }

    override fun getItemId(item: Item): Identifier {
        return Registries.ITEM.getId(item)
    }

    override fun getAllBlocks(): Collection<Block> {
        return Registries.BLOCK.toList()
    }

    override fun getAllItems(): Collection<Item> {
        return Registries.ITEM.toList()
    }

    override fun parseIdentifier(id: String): Identifier {
        val parts = id.split(":", limit = 2)
        return if (parts.size == 2) {
            Identifier.of(parts[0], parts[1])
        } else {
            Identifier.of("minecraft", id)
        }
    }

    override fun identifierOf(namespace: String, path: String): Identifier {
        return Identifier.of(namespace, path)
    }

    override fun blockStateToString(state: BlockState): String {
        return BlockArgumentParser.stringifyBlockState(state)
    }

    override fun stringToBlockState(str: String): BlockState? {
        val registryManager = getRegistryManager() ?: return null
        return try {
            BlockArgumentParser.block(
                registryManager.getOrThrow(RegistryKeys.BLOCK),
                str,
                false
            ).blockState()
        } catch (e: Exception) {
            null
        }
    }

    override fun itemStackToNbt(stack: ItemStack): NbtCompound {
        // TODO: Implement proper NBT serialization for 1.21.11
        return NbtCompound()
    }

    override fun nbtToItemStack(nbt: NbtCompound): ItemStack {
        // TODO: Implement proper NBT deserialization for 1.21.11
        return ItemStack.EMPTY
    }

    override fun shouldUseNonConsumingKeybind(): Boolean {
        // 1.21.8+ handles keybind conflicts properly with wasPressed()
        return false
    }

    fun playSoundClient(
        world: net.minecraft.client.world.ClientWorld,
        x: Double,
        y: Double,
        z: Double,
        sound: net.minecraft.sound.SoundEvent,
        soundCategory: net.minecraft.sound.SoundCategory,
        volume: Float,
        pitch: Float
    ) {
        world.playSoundClient(x, y, z, sound, soundCategory, volume, pitch, false)
    }

    fun getMainInventoryStacks(inventory: net.minecraft.entity.player.PlayerInventory): List<ItemStack> {
        return inventory.mainStacks
    }

    fun getScaledMouseX(client: MinecraftClient): Double {
        return client.mouse.getScaledX(client.window)
    }

    fun getScaledMouseY(client: MinecraftClient): Double {
        return client.mouse.getScaledY(client.window)
    }
}

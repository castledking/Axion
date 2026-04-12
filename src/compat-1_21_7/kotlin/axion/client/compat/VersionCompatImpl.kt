package axion.client.compat

import axion.common.compat.VersionCompat
import com.mojang.serialization.DynamicOps
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtOps
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

/**
 * Version compatibility implementation for Minecraft 1.21.5 - 1.21.7
 * Uses Codec-based NBT serialization introduced in 1.21.5
 */
object VersionCompatImpl : VersionCompat {

    private fun getRegistryManager(): DynamicRegistryManager? {
        return MinecraftClient.getInstance().world?.registryManager
    }

    private fun getRegistryOps(): DynamicOps<NbtElement>? {
        val registryManager = getRegistryManager() ?: return null
        return RegistryOps.of(NbtOps.INSTANCE, registryManager)
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

    // NBT serialization - 1.21.5+ uses Codec-based approach
    override fun itemStackToNbt(stack: ItemStack): NbtCompound {
        val nbt = NbtCompound()
        val ops = getRegistryOps() ?: return nbt
        // Use copyFromCodec to serialize ItemStack to NBT (returns Unit, mutates nbt)
        nbt.copyFromCodec(ItemStack.MAP_CODEC, ops, stack)
        return nbt
    }

    override fun nbtToItemStack(nbt: NbtCompound): ItemStack {
        val ops = getRegistryOps() ?: return ItemStack.EMPTY
        // Use decode to deserialize NBT to ItemStack
        return nbt.decode(ItemStack.MAP_CODEC, ops).orElse(ItemStack.EMPTY)
    }

    // Sound playback - 1.21.7 uses 9-parameter version with useDistance
    fun playSoundClient(
        world: ClientWorld,
        x: Double,
        y: Double,
        z: Double,
        sound: SoundEvent,
        soundCategory: SoundCategory,
        volume: Float,
        pitch: Float
    ) {
        world.playSoundClient(x, y, z, sound, soundCategory, volume, pitch, false)
    }

    // Inventory access - uses mainStacks
    fun getMainInventoryStacks(inventory: net.minecraft.entity.player.PlayerInventory): List<ItemStack> {
        return inventory.mainStacks
    }

    // Mouse scaling - uses built-in methods
    fun getScaledMouseX(client: MinecraftClient): Double {
        return client.mouse.getScaledX(client.window)
    }

    fun getScaledMouseY(client: MinecraftClient): Double {
        return client.mouse.getScaledY(client.window)
    }

    override fun shouldUseNonConsumingKeybind(): Boolean {
        // 1.21.7 and earlier need non-consuming keybinds to handle conflicts
        return true
    }
}

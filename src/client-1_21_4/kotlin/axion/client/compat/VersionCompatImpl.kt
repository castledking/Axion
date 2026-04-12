package axion.client.compat

import axion.common.compat.VersionCompat
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

/**
 * Version compatibility implementation for Minecraft 1.21.0 - 1.21.4
 * Uses legacy APIs that changed in 1.21.5+
 */
object VersionCompatImpl : VersionCompat {

    private fun getRegistryManager(): DynamicRegistryManager? {
        return MinecraftClient.getInstance().world?.registryManager
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

    // In 1.21+, use Identifier.of() with string parsing
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

    // NBT serialization - uses encode/decode methods
    override fun itemStackToNbt(stack: ItemStack): NbtCompound {
        return stack.encode(registryManagerOrThrow())
    }

    override fun nbtToItemStack(nbt: NbtCompound): ItemStack {
        return ItemStack.fromNbt(registryManagerOrThrow(), nbt)
    }

    // Sound playback - legacy API without distance parameter
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
        world.playSound(x, y, z, sound, soundCategory, volume, pitch, false)
    }

    // Inventory access - uses main field instead of mainStacks
    fun getMainInventoryStacks(inventory: PlayerInventory): List<ItemStack> {
        return inventory.main.toList()
    }

    // Mouse scaling - manual calculation for pre-1.21.5
    fun getScaledMouseX(client: MinecraftClient): Double {
        val window = client.window
        val mouseX = client.mouse.x
        val scaleFactor = window.scaleFactor
        return mouseX / scaleFactor
    }

    fun getScaledMouseY(client: MinecraftClient): Double {
        val window = client.window
        val mouseY = client.mouse.y
        val scaleFactor = window.scaleFactor
        return mouseY / scaleFactor
    }

    private fun registryManagerOrThrow(): DynamicRegistryManager {
        return getRegistryManager()
            ?: throw IllegalStateException("Registry manager not available")
    }

    override fun shouldUseNonConsumingKeybind(): Boolean {
        // 1.21.4 and earlier need non-consuming keybinds to handle conflicts
        return true
    }
}

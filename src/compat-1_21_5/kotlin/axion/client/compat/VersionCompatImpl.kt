package axion.client.compat

import axion.common.compat.VersionCompat
import axion.client.render.AxionWorldRenderContext
import axion.common.model.ClipboardBuffer
import com.mojang.serialization.DynamicOps
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.client.world.ClientWorld
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.entity.Entity
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
import net.minecraft.client.render.Camera
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback

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
                false,
            ).blockState()
        } catch (e: Exception) {
            null
        }
    }

    override fun itemStackToNbt(stack: ItemStack): NbtCompound {
        val nbt = NbtCompound()
        val ops = getRegistryOps() ?: return nbt
        nbt.copyFromCodec(ItemStack.MAP_CODEC, ops, stack)
        return nbt
    }

    override fun nbtToItemStack(nbt: NbtCompound): ItemStack {
        val ops = getRegistryOps() ?: return ItemStack.EMPTY
        return nbt.decode(ItemStack.MAP_CODEC, ops).orElse(ItemStack.EMPTY)
    }

    fun playSoundClient(
        world: ClientWorld,
        x: Double,
        y: Double,
        z: Double,
        sound: SoundEvent,
        soundCategory: SoundCategory,
        volume: Float,
        pitch: Float,
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

    override fun shouldUseNonConsumingKeybind(): Boolean {
        return true
    }

    fun supportsChunkedPreview(): Boolean {
        return false
    }

    fun renderChunkedPreview(
        sessionId: String,
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int,
        alpha: Int,
    ): Boolean {
        return false
    }

    fun closeChunkedPreviews() {
    }

    fun registerHudElements(
        hudId: Identifier,
        hintHudId: Identifier,
        hudRenderer: (DrawContext, RenderTickCounter) -> Unit,
        hintRenderer: (DrawContext, RenderTickCounter) -> Unit,
    ) {
        HudRenderCallback.EVENT.register { context, tickCounter ->
            hudRenderer(context, tickCounter)
            hintRenderer(context, tickCounter)
        }
    }

    fun captureEntityData(entity: Entity): NbtCompound? {
        val tag = NbtCompound()
        return if (entity.saveSelfNbt(tag)) tag else null
    }

    fun drawGuiTexture(
        context: DrawContext,
        texture: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, 0.0f, 0.0f, width, height, width, height)
    }

    fun getCameraPos(camera: Camera): Vec3d {
        return camera.pos
    }
}

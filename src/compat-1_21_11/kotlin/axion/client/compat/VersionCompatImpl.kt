package axion.client.compat

import axion.common.compat.VersionCompat
import axion.client.render.AxionWorldRenderContext
import axion.client.render.gpu.ChunkedPreviewLifecycle
import axion.common.model.ClipboardBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.Camera
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.entity.Entity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import org.joml.Matrix4fc
import org.joml.Vector3fc
import org.joml.Vector4fc

/**
 * Version compatibility implementation for Minecraft 1.21.11
 */
object VersionCompatImpl : VersionCompat {
    private var currentAtlasSampler: net.minecraft.client.gl.GpuSampler? = null

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

    fun supportsChunkedPreview(): Boolean {
        return true
    }

    fun renderChunkedPreview(
        sessionId: String,
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int,
        alpha: Int,
    ): Boolean {
        val session = ChunkedPreviewLifecycle.acquire(sessionId)
        session.setFromClipboard(clipboard, origins)
        session.render(context, color, alpha)
        return true
    }

    fun closeChunkedPreviews() {
        ChunkedPreviewLifecycle.closeAll()
    }

    // Rendering compatibility for 1.21.11
    fun getBlockAtlasTextureView(client: MinecraftClient): GpuTextureView? {
        // 1.21.11 uses atlasManager.getAtlasTexture()
        return try {
            val atlas = client.atlasManager?.getAtlasTexture(net.minecraft.util.Identifier.of("minecraft", "blocks"))
            currentAtlasSampler = atlas?.getSampler()
            atlas?.getGlTextureView()
        } catch (e: Exception) {
            null
        }
    }

    fun bindTextureToRenderPass(pass: RenderPass, samplerName: String, textureView: GpuTextureView) {
        // 1.21.11 uses bindTexture with name, view, and sampler
        val sampler = currentAtlasSampler ?: return
        pass.bindTexture(samplerName, textureView, sampler)
    }

    fun writeDynamicUniforms(
        dynamicUniforms: net.minecraft.client.gl.DynamicUniforms,
        mvMatrix: Matrix4fc,
        colorTint: Vector4fc,
        zeroVec: Vector3fc,
        normalMatrix: Matrix4fc,
        lineWidth: Float
    ): GpuBufferSlice {
        // 1.21.11 DynamicUniforms.write takes 4 parameters (lineWidth is not passed here)
        return dynamicUniforms.write(mvMatrix, colorTint, zeroVec, normalMatrix)
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

    fun registerHudElements(
        hudId: Identifier,
        hintHudId: Identifier,
        hudRenderer: (DrawContext, RenderTickCounter) -> Unit,
        hintRenderer: (DrawContext, RenderTickCounter) -> Unit,
    ) {
        // 1.21.11 uses HudRenderCallback (deprecated but still works)
        HudRenderCallback.EVENT.register { context, tickCounter ->
            hudRenderer(context, tickCounter)
            hintRenderer(context, tickCounter)
        }
    }

    fun captureEntityData(entity: Entity): NbtCompound? {
        // 1.21.11 - provide stub implementation
        // TODO: Implement properly when API is understood
        return null
    }

    fun drawGuiTexture(
        context: DrawContext,
        texture: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        // 1.21.11 - provide stub implementation
        // TODO: Implement properly when API is understood
    }

    fun getCameraPos(camera: Camera): Vec3d {
        // In 1.21.11, camera.pos is private
        // Use Fabric Loom access transformer or return a default value
        // For now, return zero vector as fallback
        return Vec3d.ZERO
    }
}

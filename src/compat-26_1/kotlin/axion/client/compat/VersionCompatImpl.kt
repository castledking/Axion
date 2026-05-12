package axion.client.compat

import axion.client.render.AxionWorldRenderContext
// Stub for 26.1.2 - SectionDrawEntry may have changed or been removed
// import axion.client.render.gpu.SectionDrawEntry
import axion.common.model.ClipboardBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.Camera
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.item.Item
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import axion.client.network.AxionPluginPayload
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.text.Text
import net.minecraft.text.MutableText
import net.minecraft.util.Formatting
import axion.client.config.formatted
import axion.client.network.BlockWrite
import axion.common.model.BlockEntityDataSnapshot
import net.minecraft.world.World
import net.minecraft.block.entity.BlockEntity
import io.netty.buffer.Unpooled
import net.minecraft.block.BlockState
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.RegistryByteBuf
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.lwjgl.glfw.GLFW
import axion.common.compat.VersionCompat

object VersionCompatImpl : VersionCompat {
    override fun getBlock(id: Identifier): Block? {
        return Registries.BLOCK.getOptional(id).orElse(null)
    }

    override fun getItem(id: Identifier): Item? {
        return Registries.ITEM.getOptional(id).orElse(null)
    }

    override fun getBlockId(block: Block): Identifier {
        return Registries.BLOCK.getKey(block)
    }

    override fun getItemId(item: Item): Identifier {
        return Registries.ITEM.getKey(item)
    }

    override fun getAllBlocks(): Collection<Block> {
        return Registries.BLOCK.toList()
    }

    override fun getAllItems(): Collection<Item> {
        return Registries.ITEM.toList()
    }

    override fun parseIdentifier(id: String): Identifier {
        return Identifier.parse(id)
    }

    override fun identifierOf(namespace: String, path: String): Identifier {
        return Identifier.fromNamespaceAndPath(namespace, path)
    }

    override fun blockStateToString(state: BlockState): String {
        return state.toString()
    }

    override fun stringToBlockState(str: String): BlockState? {
        return null
    }

    override fun itemStackToNbt(stack: ItemStack): NbtCompound {
        return NbtCompound()
    }

    override fun nbtToItemStack(nbt: NbtCompound): ItemStack {
        return ItemStack.EMPTY
    }

    override fun shouldUseNonConsumingKeybind(): Boolean = false

    fun supportsChunkedPreview(): Boolean = false

    fun renderChunkedPreview(
        sessionId: String,
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int,
        alpha: Int,
    ): Boolean = false

    fun closeChunkedPreviews() {
    }

    fun drawMultipleIndexedPreview(
        pass: RenderPass,
        drawList: List<Any>, // Stub for 26.1.2 - SectionDrawEntry may have changed or been removed
        uniformSlices: List<GpuBufferSlice>,
    ): Boolean = false

    fun writeDynamicUniforms(
        dynamicUniforms: net.minecraft.client.gl.DynamicUniforms,
        mvMatrix: org.joml.Matrix4fc,
        colorTint: org.joml.Vector4fc,
        zeroVec: org.joml.Vector3fc,
        normalMatrix: org.joml.Matrix4fc,
        lineWidth: Float,
    ): GpuBufferSlice {
        TODO("Dynamic uniforms are not ported to Minecraft 26.1.2 yet")
    }

    fun getBlockAtlasTextureView(client: MinecraftClient): GpuTextureView? = null

    fun bindTextureToRenderPass(pass: RenderPass, samplerName: String, textureView: GpuTextureView) {
    }

    fun getRenderPipeline(layer: RenderLayer): RenderPipeline {
        return RenderPipelines.LINES
    }

    fun getPreviewShellPipeline(vertexFormat: VertexFormat, drawMode: Any): RenderPipeline {
        return RenderPipelines.LINES
    }

    fun playSoundClient(
        world: net.minecraft.client.world.ClientWorld,
        x: Double,
        y: Double,
        z: Double,
        sound: net.minecraft.sound.SoundEvent,
        soundCategory: net.minecraft.sound.SoundCategory,
        volume: Float,
        pitch: Float,
    ) {
        world.playLocalSound(x, y, z, sound, soundCategory, volume, pitch, false)
    }

    fun getMainInventoryStacks(inventory: net.minecraft.entity.player.PlayerInventory): List<ItemStack> {
        return inventory.nonEquipmentItems
    }

    fun getScaledMouseX(client: MinecraftClient): Double {
        return client.mouseHandler.xpos() * client.window.guiScaledWidth / client.window.screenWidth
    }

    fun getScaledMouseY(client: MinecraftClient): Double {
        return client.mouseHandler.ypos() * client.window.guiScaledHeight / client.window.screenHeight
    }

    fun onPlayJoin(handler: (client: MinecraftClient, sender: net.fabricmc.fabric.api.networking.v1.PacketSender) -> Unit) {
        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { handler, sender, client ->
            handler(client, sender)
        })
    }

    fun onPlayDisconnect(handler: (client: MinecraftClient) -> Unit) {
        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { handler, client ->
            handler(client)
        })
    }

    fun getModVersion(modId: String): String {
        return FabricLoader.getInstance()
            .getModContainer(modId)
            .orElseThrow()
            .metadata
            .version
            .friendlyString
    }

    fun notifyPlayer(player: net.minecraft.client.player.LocalPlayer?, text: Text, overlay: Boolean) {
        if (overlay) player?.sendOverlayMessage(text) else player?.sendSystemMessage(text)
    }

    fun hasLocalServer(client: MinecraftClient): Boolean = client.hasSingleplayerServer()

    fun runOnRenderThread(client: MinecraftClient, task: Runnable) {
        client.execute(task)
    }

    fun createLiteral(text: String): net.minecraft.network.chat.Component = net.minecraft.network.chat.Component.literal(text)
    fun formatText(text: net.minecraft.network.chat.Component, formatting: net.minecraft.ChatFormatting): net.minecraft.network.chat.MutableComponent = text.copy().withStyle(formatting)

    fun captureBlockEntity(world: World, pos: BlockPos): BlockEntityDataSnapshot? {
        val blockEntity = world.getBlockEntity(pos) ?: return null
        val nbt = blockEntity.saveWithFullMetadata(world.registryAccess())
        return BlockEntityDataSnapshot(nbt.copy())
    }

    fun applyBlockEntity(world: World, write: BlockWrite) {
        world.setBlock(write.pos, write.state, 3)
        val payload = write.blockEntityData
        if (payload == null) {
            world.removeBlockEntity(write.pos)
            val provider = write.state.block as? net.minecraft.world.level.block.EntityBlock ?: return
            val entity = provider.newBlockEntity(write.pos, write.state) ?: return
            world.getChunkAt(write.pos).setBlockEntity(entity)
            entity.setChanged()
            return
        }

        val restored = payload.nbt.copy()
        restored.putInt("x", write.pos.x)
        restored.putInt("y", write.pos.y)
        restored.putInt("z", write.pos.z)
        val blockEntity = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(write.pos, write.state, restored, world.registryAccess())
            ?: return
        world.removeBlockEntity(write.pos)
        world.getChunkAt(write.pos).setBlockEntity(blockEntity)
        blockEntity.setChanged()
    }

    // Rendering helpers for 26.1.2 - using stubs due to major API changes
    override fun getBlockRenderManager(client: Any): Any {
        return client // Stub - actual implementation needs 26.1.2 specific API
    }

    override fun getBlockRenderType(state: BlockState): Any {
        return state // Stub - actual implementation needs 26.1.2 specific API
    }

    override fun getRenderingSeed(state: BlockState, pos: Any): Long {
        return 0L // Stub - actual implementation needs 26.1.2 specific API
    }

    override fun matrixStackPush(stack: Any): Any {
        return stack // Stub - actual implementation needs 26.1.2 specific API
    }

    override fun matrixStackPop(stack: Any) {
        // Stub - actual implementation needs 26.1.2 specific API
    }

    override fun blockRenderManagerGetModel(manager: Any, state: BlockState): Any {
        return manager // Stub
    }

    override fun blockRenderManagerRenderBlock(manager: Any, state: BlockState, pos: Any, world: Any, matrixStack: Any, consumer: Any, checkSides: Boolean, parts: List<Any>): Boolean {
        return false // Stub
    }

    override fun blockRenderManagerRenderFluid(manager: Any, pos: Any, world: Any, consumer: Any, state: BlockState, fluidState: Any): Boolean {
        return false // Stub
    }

    // Entity API helpers for 26.1.2
    override fun entityIsRemoved(entity: Any): Boolean {
        return (entity as net.minecraft.world.entity.Entity).isRemoved()
    }

    override fun entityGetVehicle(entity: Any): Any? {
        return (entity as net.minecraft.world.entity.Entity).vehicle
    }

    override fun entityGetUuid(entity: Any): java.util.UUID {
        return (entity as net.minecraft.world.entity.Entity).uuid
    }

    override fun entityGetX(entity: Any): Double {
        return (entity as net.minecraft.world.entity.Entity).x
    }

    override fun entityGetY(entity: Any): Double {
        return (entity as net.minecraft.world.entity.Entity).y
    }

    override fun entityGetZ(entity: Any): Double {
        return (entity as net.minecraft.world.entity.Entity).z
    }

    override fun entityGetYaw(entity: Any): Float {
        return (entity as net.minecraft.world.entity.Entity).getYRot()
    }

    override fun entityGetPitch(entity: Any): Float {
        return (entity as net.minecraft.world.entity.Entity).getXRot()
    }

    override fun entityGetPassengerList(entity: Any): List<Any> {
        return (entity as net.minecraft.world.entity.Entity).passengers
    }

    override fun entitySetUuid(entity: Any, uuid: java.util.UUID) {
        (entity as net.minecraft.world.entity.Entity).setUUID(uuid)
    }

    override fun entityRefreshPositionAndAngles(entity: Any) {
        val e = entity as net.minecraft.world.entity.Entity
        e.setPosRaw(e.x, e.y, e.z)
        e.setYRot(e.getYRot())
        e.setXRot(e.getXRot())
    }

    override fun entityUpdatePassengerPosition(entity: Any, passenger: Any) {
        val e = entity as net.minecraft.world.entity.Entity
        val p = passenger as net.minecraft.world.entity.Entity
        p.setPos(e.x, e.y, e.z)
    }

    override fun entityTypeLoadEntityWithPassengers(tag: NbtCompound, world: Any, spawnReason: Any, entityProcessor: (Any) -> Any): Any? {
        // 26.1.2 API for loading entities with passengers is different
        // Return null for now - actual implementation needs 26.1.2 specific API
        return null
    }

    override fun worldSpawnNewEntityAndPassengers(world: Any, entity: Any): Boolean {
        // 26.1.2 API - addFreshEntityWithPassengers returns Unit
        (world as net.minecraft.server.level.ServerLevel).addFreshEntityWithPassengers(
            entity as net.minecraft.world.entity.Entity
        )
        return true
    }

    override fun worldGetOtherEntities(world: Any, entity: Any, box: Any): List<Any> {
        return (world as net.minecraft.world.level.Level).getEntitiesOfClass(
            net.minecraft.world.entity.Entity::class.java,
            box as net.minecraft.world.phys.AABB
        ).filter { it != entity }
    }

    // MinecraftClient API helpers for 26.1.2
    override fun clientGetServer(client: Any): Any? {
        return (client as MinecraftClient).getSingleplayerServer()
    }

    override fun clientGetWorldRegistryKey(client: Any): Any? {
        return (client as MinecraftClient).level?.dimension()
    }

    override fun serverExecute(server: Any, task: Runnable) {
        (server as net.minecraft.client.server.IntegratedServer).execute(task)
    }

    @Suppress("UNCHECKED_CAST")
    override fun serverGetWorld(server: Any, registryKey: Any): Any? {
        return (server as net.minecraft.client.server.IntegratedServer).getLevel(
            registryKey as net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>
        )
    }

    override fun playerSendMessage(player: Any, message: Any, overlay: Boolean) {
        val p = player as net.minecraft.world.entity.player.Player
        val msg = message as net.minecraft.network.chat.Component
        if (overlay) {
            (p as? net.minecraft.client.player.LocalPlayer)?.sendOverlayMessage(msg)
                ?: p.sendSystemMessage(msg)
        } else {
            p.sendSystemMessage(msg)
        }
    }

    // Direction/BlockState API helpers for 26.1.2
    override fun directionGetVector(direction: Any): Any {
        // 26.1.2 API for direction vector is different
        val d = direction as net.minecraft.core.Direction
        return net.minecraft.core.Vec3i(d.step().x.toInt(), d.step().y.toInt(), d.step().z.toInt())
    }

    override fun blockStateStringify(state: BlockState): String {
        return state.toString()
    }

    // Registry/BlockArgumentParser API helpers for 26.1.2
    override fun worldGetRegistryManager(world: Any): Any {
        // 26.1.2 API for registry manager is different
        // Stub - actual implementation needs 26.1.2 specific API
        return (world as net.minecraft.world.level.Level).registryAccess()
    }

    override fun blockArgumentParserBlock(registry: Any, state: String): Any {
        // 26.1.2 API for BlockArgumentParser.block is different
        // Return a stub placeholder - actual implementation needs 26.1.2 specific API
        return net.minecraft.world.level.block.Blocks.AIR
    }

    fun registerAxionPayloadChannel(
        id: CustomPayload.Id<AxionPluginPayload>,
        codec: PacketCodec<RegistryByteBuf, AxionPluginPayload>,
    ) {
        PayloadTypeRegistry.serverboundPlay().register(id.delegate, codec)
        PayloadTypeRegistry.clientboundPlay().register(id.delegate, codec)
    }

    fun registerAxionReceiver(
        id: CustomPayload.Id<AxionPluginPayload>,
        handler: (AxionPluginPayload) -> Unit,
    ) {
        ClientPlayNetworking.registerGlobalReceiver(id.delegate) { payload, context ->
            context.client().execute {
                handler(payload as AxionPluginPayload)
            }
        }
    }

    fun sendAxionPayload(payload: AxionPluginPayload) {
        ClientPlayNetworking.send(payload)
    }

    fun registerHudElements(
        hudId: Identifier,
        hintHudId: Identifier,
        hudRenderer: (DrawContext, RenderTickCounter) -> Unit,
        hintRenderer: (DrawContext, RenderTickCounter) -> Unit,
    ) {
        // 26.1.x: Use HudElementRegistry to attach after HOTBAR
        val hotbar = net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
            hotbar,
            hudId,
            net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement { extractor, deltaTracker ->
                hudRenderer(DrawContext(extractor), deltaTracker)
            }
        )
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
            hudId,
            hintHudId,
            net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement { extractor, deltaTracker ->
                hintRenderer(DrawContext(extractor), deltaTracker)
            }
        )
    }

    fun captureEntityData(entity: Entity): NbtCompound? = null

    fun drawGuiTexture(
        context: DrawContext,
        texture: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        // In 26.1.x, the blit API changed. Use the delegate's blitSprite directly.
        context.delegate.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x, y, width, height)
    }

    fun getCameraPos(camera: Camera): Vec3d {
        return camera.position()
    }

    // ItemStack codec helper for 26.1.x
    override fun itemStackEncode(registryManager: Any, stack: Any): ByteArray? {
        return runCatching {
            val buf = RegistryByteBuf(Unpooled.buffer(), registryManager as net.minecraft.registry.DynamicRegistryManager)
            // Try different field names for the codec
            val codec = runCatching { ItemStack::class.java.getField("PACKET_CODEC").get(null) }
                .getOrElse { ItemStack::class.java.getField("STREAM_CODEC").get(null) }
            codec.javaClass.methods
                .first { it.name == "encode" && it.parameterTypes.size == 2 }
                .invoke(codec, buf, stack)
            val bytes = ByteArray(buf.readableBytes())
            buf.getBytes(0, bytes)
            bytes
        }.getOrNull()
    }

    override fun itemStackDecode(registryManager: Any, bytes: ByteArray): Any? {
        return runCatching {
            val buf = RegistryByteBuf(Unpooled.wrappedBuffer(bytes), registryManager as net.minecraft.registry.DynamicRegistryManager)
            val codec = runCatching { ItemStack::class.java.getField("PACKET_CODEC").get(null) }
                .getOrElse { ItemStack::class.java.getField("STREAM_CODEC").get(null) }
            codec.javaClass.methods
                .first { it.name == "decode" && it.parameterTypes.size == 1 }
                .invoke(codec, buf)
        }.getOrNull()
    }
}

package axion.client.compat

import axion.client.network.AxionPluginPayload
import axion.client.network.BlockWrite
import axion.client.network.BlockWriteUpdatePolicy
import axion.client.render.AxionWorldRenderContext
import axion.client.render.ShaderPackCompat
import axion.client.render.gpu.ChunkedPreviewLifecycle
import axion.common.compat.VersionCompat
import axion.common.model.BlockEntityDataSnapshot
import axion.common.model.ClipboardBuffer
import com.mojang.serialization.DynamicOps
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.Camera
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
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object VersionCompatImpl : VersionCompat {
    private fun getRegistryManager(): DynamicRegistryManager? =
        MinecraftClient.getInstance().world?.registryManager

    private fun getRegistryOps(): DynamicOps<NbtElement>? {
        val registryManager = getRegistryManager() ?: return null
        return RegistryOps.of(NbtOps.INSTANCE, registryManager)
    }

    override fun getBlock(id: Identifier): Block? = Registries.BLOCK.get(id).takeUnless {
        it == net.minecraft.block.Blocks.AIR && id != Registries.BLOCK.getId(net.minecraft.block.Blocks.AIR)
    }

    override fun getItem(id: Identifier): Item? = Registries.ITEM.get(id).takeUnless {
        it == net.minecraft.item.Items.AIR && id != Registries.ITEM.getId(net.minecraft.item.Items.AIR)
    }

    override fun getBlockId(block: Block): Identifier = Registries.BLOCK.getId(block)
    override fun getItemId(item: Item): Identifier = Registries.ITEM.getId(item)
    override fun getAllBlocks(): Collection<Block> = Registries.BLOCK.toList()
    override fun getAllItems(): Collection<Item> = Registries.ITEM.toList()

    override fun parseIdentifier(id: String): Identifier {
        val parts = id.split(":", limit = 2)
        return if (parts.size == 2) Identifier.of(parts[0], parts[1]) else Identifier.of("minecraft", id)
    }

    override fun identifierOf(namespace: String, path: String): Identifier = Identifier.of(namespace, path)
    override fun blockStateToString(state: BlockState): String = BlockArgumentParser.stringifyBlockState(state)

    override fun stringToBlockState(str: String): BlockState? {
        val registryManager = getRegistryManager() ?: return null
        return runCatching {
            BlockArgumentParser.block(registryManager.getOrThrow(RegistryKeys.BLOCK), str, false).blockState()
        }.getOrNull()
    }

    override fun itemStackToNbt(stack: ItemStack): NbtCompound {
        val registryManager = getRegistryManager() ?: return NbtCompound()
        return stack.toNbt(registryManager) as? NbtCompound ?: NbtCompound()
    }

    override fun nbtToItemStack(nbt: NbtCompound): ItemStack {
        val registryManager = getRegistryManager() ?: return ItemStack.EMPTY
        return ItemStack.fromNbtOrEmpty(registryManager, nbt)
    }

    override fun shouldUseNonConsumingKeybind(): Boolean = true

    fun onPlayJoin(handler: (client: MinecraftClient, sender: PacketSender) -> Unit) {
        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, sender, client ->
            handler(client, sender)
        })
    }

    fun onPlayDisconnect(handler: (client: MinecraftClient) -> Unit) {
        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, client ->
            handler(client)
        })
    }

    fun getModVersion(modId: String): String =
        FabricLoader.getInstance().getModContainer(modId).orElseThrow().metadata.version.friendlyString

    fun notifyPlayer(player: net.minecraft.client.network.ClientPlayerEntity?, text: Text, overlay: Boolean) {
        player?.sendMessage(text, overlay)
    }

    fun hasLocalServer(client: MinecraftClient): Boolean = client.server != null
    fun runOnRenderThread(client: MinecraftClient, task: Runnable) = client.execute(task)
    fun createLiteral(text: String): MutableText = Text.literal(text)
    fun formatText(text: MutableText, formatting: Formatting): MutableText = text.formatted(formatting)

    fun captureBlockEntity(world: net.minecraft.world.World, pos: BlockPos): BlockEntityDataSnapshot? {
        val blockEntity = world.getBlockEntity(pos) ?: return null
        return BlockEntityDataSnapshot(blockEntity.createNbtWithIdentifyingData(world.registryManager).copy())
    }

    fun applyBlockEntity(world: net.minecraft.world.World, write: BlockWrite) {
        world.setBlockState(write.pos, write.state, BlockWriteUpdatePolicy.LEGACY_NO_PHYSICS_FLAGS)
        suppressLegacyScheduledUpdates(world, write.pos)
        val payload = write.blockEntityData
        if (payload == null) {
            world.removeBlockEntity(write.pos)
            val provider = write.state.block as? net.minecraft.block.BlockEntityProvider ?: return
            val blockEntity = provider.createBlockEntity(write.pos, write.state) ?: return
            world.getChunk(write.pos.x shr 4, write.pos.z shr 4).setBlockEntity(blockEntity)
            blockEntity.markDirty()
            return
        }
        val restored = payload.nbt.copy()
        restored.putInt("x", write.pos.x)
        restored.putInt("y", write.pos.y)
        restored.putInt("z", write.pos.z)
        val blockEntity = net.minecraft.block.entity.BlockEntity.createFromNbt(write.pos, write.state, restored, world.registryManager)
            ?: return
        world.removeBlockEntity(write.pos)
        world.getChunk(write.pos.x shr 4, write.pos.z shr 4).setBlockEntity(blockEntity)
        blockEntity.markDirty()
    }

    private fun suppressLegacyScheduledUpdates(world: net.minecraft.world.World, pos: BlockPos) {
        val serverWorld = world as? net.minecraft.server.world.ServerWorld ?: return
        val editedBlock = net.minecraft.util.math.BlockBox(pos)
        serverWorld.blockTickScheduler.clearNextTicks(editedBlock)
        serverWorld.fluidTickScheduler.clearNextTicks(editedBlock)
    }

    fun registerAxionPayloadChannel(id: CustomPayload.Id<AxionPluginPayload>, codec: PacketCodec<RegistryByteBuf, AxionPluginPayload>) {
        PayloadTypeRegistry.playC2S().register(id, codec)
        PayloadTypeRegistry.playS2C().register(id, codec)
    }

    fun registerAxionReceiver(id: CustomPayload.Id<AxionPluginPayload>, handler: (AxionPluginPayload) -> Unit) {
        ClientPlayNetworking.registerGlobalReceiver(id) { payload, context ->
            context.client().execute { handler(payload) }
        }
    }

    fun sendAxionPayload(payload: AxionPluginPayload) = ClientPlayNetworking.send(payload)
    fun supportsChunkedPreview(): Boolean = !ShaderPackCompat.shouldDisableDirectGpuPreview()

    fun renderChunkedPreview(sessionId: String, context: AxionWorldRenderContext, clipboard: ClipboardBuffer, origins: Collection<BlockPos>, color: Int, alpha: Int): Boolean {
        if (ShaderPackCompat.shouldDisableDirectGpuPreview()) return false
        val session = ChunkedPreviewLifecycle.acquire(sessionId)
        session.setFromClipboard(clipboard, origins)
        return session.render(context, color, alpha).handled
    }

    fun closeChunkedPreviews() {
        ChunkedPreviewLifecycle.closeAll()
    }

    fun registerHudElements(hudId: Identifier, hintHudId: Identifier, hudRenderer: (DrawContext, RenderTickCounter) -> Unit, hintRenderer: (DrawContext, RenderTickCounter) -> Unit) {
        HudRenderCallback.EVENT.register { context, tickCounter ->
            hudRenderer(context, tickCounter)
            hintRenderer(context, tickCounter)
        }
    }

    fun playSoundClient(world: ClientWorld, x: Double, y: Double, z: Double, sound: SoundEvent, soundCategory: SoundCategory, volume: Float, pitch: Float) {
        world.playSound(x, y, z, sound, soundCategory, volume, pitch, false)
    }

    fun getMainInventoryStacks(inventory: net.minecraft.entity.player.PlayerInventory): List<ItemStack> = inventory.main
    fun getScaledMouseX(client: MinecraftClient): Double = client.mouse.x * client.window.scaledWidth / client.window.width
    fun getScaledMouseY(client: MinecraftClient): Double = client.mouse.y * client.window.scaledHeight / client.window.height
    fun captureEntityData(entity: Entity): NbtCompound? {
        val tag = NbtCompound()
        return if (entity.saveSelfNbt(tag)) tag else null
    }
    fun drawGuiTexture(context: DrawContext, texture: Identifier, x: Int, y: Int, width: Int, height: Int) {
        context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, 0f, 0f, width, height, width, height)
    }
    fun drawGuiTextureRegion(
        context: DrawContext,
        texture: Identifier,
        x: Int,
        y: Int,
        u: Int,
        v: Int,
        width: Int,
        height: Int,
        textureWidth: Int,
        textureHeight: Int,
    ) {
        context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, u.toFloat(), v.toFloat(), width, height, textureWidth, textureHeight)
    }
    fun getCameraPos(camera: Camera): Vec3d = camera.pos
    fun rawBlockStateId(state: BlockState): Int = Block.getRawIdFromState(state)

    override fun getBlockRenderManager(client: Any): Any = (client as MinecraftClient).blockRenderManager
    override fun getBlockRenderType(state: BlockState): Any = state.renderType
    override fun getRenderingSeed(state: BlockState, pos: Any): Long = state.getRenderingSeed(pos as BlockPos)
    override fun matrixStackPush(stack: Any): Any = (stack as net.minecraft.client.util.math.MatrixStack).push()
    override fun matrixStackPop(stack: Any) = (stack as net.minecraft.client.util.math.MatrixStack).pop()
    override fun blockRenderManagerGetModel(manager: Any, state: BlockState): Any = (manager as net.minecraft.client.render.block.BlockRenderManager).getModel(state)

    @Suppress("UNCHECKED_CAST")
    override fun blockRenderManagerRenderBlock(manager: Any, state: BlockState, pos: Any, world: Any, matrixStack: Any, consumer: Any, checkSides: Boolean, parts: List<Any>): Boolean {
        val random = net.minecraft.util.math.random.Random.create()
        random.setSeed(state.getRenderingSeed(pos as BlockPos))
        (manager as net.minecraft.client.render.block.BlockRenderManager).renderBlock(state, pos, world as net.minecraft.world.BlockRenderView, matrixStack as net.minecraft.client.util.math.MatrixStack, consumer as net.minecraft.client.render.VertexConsumer, checkSides, random)
        return true
    }

    override fun blockRenderManagerRenderFluid(manager: Any, pos: Any, world: Any, consumer: Any, state: BlockState, fluidState: Any): Boolean {
        (manager as net.minecraft.client.render.block.BlockRenderManager).renderFluid(pos as BlockPos, world as net.minecraft.world.BlockRenderView, consumer as net.minecraft.client.render.VertexConsumer, state, fluidState as net.minecraft.fluid.FluidState)
        return true
    }

    override fun entityIsRemoved(entity: Any): Boolean = (entity as Entity).isRemoved
    override fun entityGetVehicle(entity: Any): Any? = (entity as Entity).vehicle
    override fun entityGetUuid(entity: Any): java.util.UUID = (entity as Entity).uuid
    override fun entityGetX(entity: Any): Double = (entity as Entity).x
    override fun entityGetY(entity: Any): Double = (entity as Entity).y
    override fun entityGetZ(entity: Any): Double = (entity as Entity).z
    override fun entityGetYaw(entity: Any): Float = (entity as Entity).yaw
    override fun entityGetPitch(entity: Any): Float = (entity as Entity).pitch
    override fun entityGetPassengerList(entity: Any): List<Any> = (entity as Entity).passengerList
    override fun entitySetUuid(entity: Any, uuid: java.util.UUID) = (entity as Entity).setUuid(uuid)

    override fun entitySetPositionAndAngles(entity: Any, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        (entity as Entity).refreshPositionAndAngles(x, y, z, yaw, pitch)
    }

    override fun entityRefreshPositionAndAngles(entity: Any) {
        val e = entity as Entity
        e.refreshPositionAndAngles(e.x, e.y, e.z, e.yaw, e.pitch)
    }

    override fun entityUpdatePassengerPosition(entity: Any, passenger: Any) =
        (entity as Entity).updatePassengerPosition(passenger as Entity)

    override fun entityTypeLoadEntityWithPassengers(tag: NbtCompound, world: Any, spawnReason: Any, entityProcessor: (Any) -> Any): Any? =
        net.minecraft.entity.EntityType.loadEntityWithPassengers(tag, world as net.minecraft.world.World, spawnReason as net.minecraft.entity.SpawnReason, java.util.function.Function { entity -> entityProcessor(entity) as Entity })

    override fun worldSpawnNewEntityAndPassengers(world: Any, entity: Any): Boolean =
        (world as net.minecraft.server.world.ServerWorld).spawnNewEntityAndPassengers(entity as Entity)

    override fun worldGetOtherEntities(world: Any, entity: Any, box: Any): List<Any> =
        (world as net.minecraft.world.World).getOtherEntities(entity as Entity, box as net.minecraft.util.math.Box)

    override fun clientGetServer(client: Any): Any? = (client as MinecraftClient).server
    override fun clientGetWorldRegistryKey(client: Any): Any? = (client as MinecraftClient).world?.registryKey
    override fun serverExecute(server: Any, task: Runnable) = (server as net.minecraft.server.integrated.IntegratedServer).execute(task)
    @Suppress("UNCHECKED_CAST")
    override fun serverGetWorld(server: Any, registryKey: Any): Any? = (server as net.minecraft.server.integrated.IntegratedServer).getWorld(registryKey as net.minecraft.registry.RegistryKey<net.minecraft.world.World>)
    override fun playerSendMessage(player: Any, message: Any, overlay: Boolean) = (player as net.minecraft.entity.player.PlayerEntity).sendMessage(message as Text, overlay)
    override fun directionGetVector(direction: Any): Any = (direction as net.minecraft.util.math.Direction).vector
    override fun blockStateStringify(state: BlockState): String = BlockArgumentParser.stringifyBlockState(state)
    override fun worldGetRegistryManager(world: Any): Any = (world as net.minecraft.world.World).registryManager
    override fun blockArgumentParserBlock(registry: Any, state: String): Any = BlockArgumentParser.block((registry as DynamicRegistryManager).getOrThrow(RegistryKeys.BLOCK), state, false)
    override fun itemStackEncode(registryManager: Any, stack: Any): ByteArray? {
        return runCatching {
            val buf = RegistryByteBuf(Unpooled.buffer(), registryManager as DynamicRegistryManager)
            ItemStack.PACKET_CODEC.encode(buf, stack as ItemStack)
            ByteArray(buf.readableBytes()).also { buf.getBytes(0, it) }
        }.getOrNull()
    }

    override fun itemStackDecode(registryManager: Any, bytes: ByteArray): Any? {
        return runCatching {
            val buf = RegistryByteBuf(Unpooled.wrappedBuffer(bytes), registryManager as DynamicRegistryManager)
            ItemStack.PACKET_CODEC.decode(buf)
        }.getOrNull()
    }

    override fun createAxionPluginPayloadCodec(): Any {
        return PacketCodec.of(
            { value: AxionPluginPayload, buf: RegistryByteBuf -> buf.writeBytes(value.bytes) },
            { buf: RegistryByteBuf ->
                val bytes = ByteArray(buf.readableBytes())
                buf.readBytes(bytes)
                AxionPluginPayload(bytes)
            },
        )
    }
}

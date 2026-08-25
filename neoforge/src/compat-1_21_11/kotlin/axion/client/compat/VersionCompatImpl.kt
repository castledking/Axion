package axion.client.compat

import axion.client.render.AxionWorldRenderContext
import axion.client.render.PreviewVisualPolicy
import axion.client.render.ShaderPackCompat
import axion.client.render.gpu.ChunkedPreviewLifecycle
import axion.client.render.gpu.SectionDrawEntry
import axion.client.network.AxionPluginPayload
import axion.client.network.BlockWrite
import axion.client.network.BlockWriteUpdatePolicy
import axion.common.compat.VersionCompat
import java.lang.reflect.Field
import axion.common.model.BlockEntityDataSnapshot
import axion.common.model.ClipboardBuffer
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import io.netty.buffer.Unpooled
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.textures.GpuSampler
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.Camera
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.DeltaTracker
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.NbtOps
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.RegistryOps
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.util.ProblemReporter
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.ChatFormatting
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import net.neoforged.fml.ModList
import org.joml.Matrix4fc
import org.joml.Vector3fc
import org.joml.Vector4fc
import org.slf4j.LoggerFactory

/**
 * Version compatibility implementation for Minecraft 1.21.11
 */
object VersionCompatImpl : VersionCompat {
    private val logger = LoggerFactory.getLogger(VersionCompatImpl::class.java)
    private var currentAtlasSampler: GpuSampler? = null
    private val previewShellPipelines = java.util.HashMap<Pair<VertexFormat.Mode, Boolean>, RenderPipeline>()

    private val clientTickHandlers = mutableListOf<(Minecraft) -> Unit>()
    private val clientStoppingHandlers = mutableListOf<(Minecraft) -> Unit>()
    private val playJoinHandlers = mutableListOf<(Minecraft) -> Unit>()
    private val playDisconnectHandlers = mutableListOf<(Minecraft) -> Unit>()
    private val clientPayloadHandlers = mutableMapOf<CustomPacketPayload.Type<AxionPluginPayload>, (AxionPluginPayload) -> Unit>()
    private var hudRenderer: ((GuiGraphics, DeltaTracker) -> Unit)? = null
    private var hintHudRenderer: ((GuiGraphics, DeltaTracker) -> Unit)? = null

    private val dynamicUniformsWrite4 by lazy {
        net.minecraft.client.renderer.DynamicUniforms::class.java.methods.firstOrNull { method ->
            method.parameterTypes.size == 4 &&
                GpuBufferSlice::class.java.isAssignableFrom(method.returnType) &&
                method.name != "equals" && method.name != "toString" && method.name != "hashCode"
        }.also {
            if (it != null) logger.info("[Axion GPU] Found DynamicUniforms.write(4-arg): {}", it.name)
        }
    }

    private val dynamicUniformsWrite5 by lazy {
        net.minecraft.client.renderer.DynamicUniforms::class.java.methods.firstOrNull { method ->
            method.parameterTypes.size == 5 &&
                GpuBufferSlice::class.java.isAssignableFrom(method.returnType) &&
                method.name != "equals" && method.name != "toString" && method.name != "hashCode"
        }.also {
            if (it != null) logger.info("[Axion GPU] Found DynamicUniforms.write(5-arg): {}", it.name)
        }
    }

    private val dynamicUniformsWriteAny by lazy {
        // Last resort: find ANY method returning GpuBufferSlice
        net.minecraft.client.renderer.DynamicUniforms::class.java.methods.filter { method ->
            GpuBufferSlice::class.java.isAssignableFrom(method.returnType) &&
                method.parameterTypes.isNotEmpty() &&
                method.name != "equals" && method.name != "toString" && method.name != "hashCode"
        }.also { methods ->
            if (methods.isNotEmpty()) {
                methods.forEach { m ->
                    logger.info("[Axion GPU] DynamicUniforms candidate: {}({}) -> {}",
                        m.name, m.parameterTypes.joinToString { it.simpleName }, m.returnType.simpleName)
                }
            } else {
                logger.warn("[Axion GPU] No DynamicUniforms methods returning GpuBufferSlice found. All methods:")
                net.minecraft.client.renderer.DynamicUniforms::class.java.methods.forEach { m ->
                    logger.warn("  {}({}) -> {}", m.name, m.parameterTypes.joinToString { it.simpleName }, m.returnType.simpleName)
                }
            }
        }
    }

    private fun getRegistryManager(): RegistryAccess? {
        return Minecraft.getInstance().level?.registryAccess()
    }

    private fun getRegistryOps(): com.mojang.serialization.DynamicOps<Tag>? {
        val registryManager = getRegistryManager() ?: return null
        return RegistryOps.create(NbtOps.INSTANCE, registryManager)
    }

    private fun registryManagerOrThrow(): RegistryAccess {
        return getRegistryManager()
            ?: throw IllegalStateException("Registry manager not available")
    }

    override fun getBlock(id: Identifier): Block? {
        val holder = BuiltInRegistries.BLOCK.get(id).orElse(null) ?: return null
        val block = holder.value()
        return if (block === net.minecraft.world.level.block.Blocks.AIR) null else block
    }

    override fun getItem(id: Identifier): Item? {
        val holder = BuiltInRegistries.ITEM.get(id).orElse(null) ?: return null
        val item = holder.value()
        return if (item === net.minecraft.world.item.Items.AIR) null else item
    }

    override fun getBlockId(block: Block): Identifier {
        return BuiltInRegistries.BLOCK.getKey(block)
    }

    override fun getItemId(item: Item): Identifier {
        return BuiltInRegistries.ITEM.getKey(item)
    }

    override fun getAllBlocks(): Collection<Block> {
        return BuiltInRegistries.BLOCK.toList()
    }

    override fun getAllItems(): Collection<Item> {
        return BuiltInRegistries.ITEM.toList()
    }

    override fun parseIdentifier(id: String): Identifier {
        val parts = id.split(":", limit = 2)
        return if (parts.size == 2) {
            Identifier.fromNamespaceAndPath(parts[0], parts[1])
        } else {
            Identifier.fromNamespaceAndPath("minecraft", id)
        }
    }

    override fun identifierOf(namespace: String, path: String): Identifier {
        return Identifier.fromNamespaceAndPath(namespace, path)
    }

    override fun blockStateToString(state: BlockState): String {
        return net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state)
    }

    override fun stringToBlockState(str: String): BlockState? {
        val registryManager = getRegistryManager() ?: return null
        return try {
            val holderLookup = registryManager.lookupOrThrow(Registries.BLOCK)
            val result: net.minecraft.commands.arguments.blocks.BlockStateParser.BlockResult =
                net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(holderLookup, str, false)
            result.blockState()
        } catch (e: Exception) {
            null
        }
    }

    override fun itemStackToNbt(stack: ItemStack): CompoundTag {
        val nbt = CompoundTag()
        val ops = getRegistryOps() ?: return nbt
        ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent { encoded -> nbt.merge(encoded as CompoundTag) }
        return nbt
    }

    override fun nbtToItemStack(nbt: CompoundTag): ItemStack {
        val ops = getRegistryOps() ?: return ItemStack.EMPTY
        return ItemStack.CODEC.parse(ops, nbt).result().orElse(ItemStack.EMPTY)
    }

    override fun shouldUseNonConsumingKeybind(): Boolean {
        // 1.21.8+ handles keybind conflicts properly with consumeClick()
        return false
    }

    fun onEndClientTick(handler: (Minecraft) -> Unit) {
        clientTickHandlers += handler
    }

    fun onClientStopping(handler: (Minecraft) -> Unit) {
        clientStoppingHandlers += handler
    }

    fun onPlayJoin(handler: (client: Minecraft, sender: Any?) -> Unit) {
        playJoinHandlers += { client -> handler(client, null) }
    }

    fun onPlayDisconnect(handler: (client: Minecraft) -> Unit) {
        playDisconnectHandlers += handler
    }

    fun fireClientTick(client: Minecraft) {
        clientTickHandlers.forEach { it(client) }
    }

    fun firePlayJoin(client: Minecraft) {
        playJoinHandlers.forEach { it(client) }
    }

    fun firePlayDisconnect(client: Minecraft) {
        clientStoppingHandlers.forEach { it(client) }
        playDisconnectHandlers.forEach { it(client) }
    }

    fun fireServerTick(server: MinecraftServer) {
        NoClipService.onServerTick(server)
    }

    fun getModVersion(modId: String): String {
        return ModList.get().getModContainerById(modId)
            .map { it.modInfo.version.toString() }
            .orElseThrow()
    }

    fun notifyPlayer(player: net.minecraft.client.player.LocalPlayer?, text: Component, overlay: Boolean) {
        player?.displayClientMessage(text, overlay)
    }

    fun sendGameModeCommand(client: Minecraft, gameModeId: String) {
        client.connection?.sendCommand("gamemode $gameModeId")
    }

    fun changeLocalGameMode(client: Minecraft, gameModeId: String): Boolean {
        val server = client.singleplayerServer ?: return false
        val playerId = client.player?.uuid ?: return false
        val gameMode = when (gameModeId.lowercase()) {
            "survival" -> net.minecraft.world.level.GameType.SURVIVAL
            "creative" -> net.minecraft.world.level.GameType.CREATIVE
            "spectator" -> net.minecraft.world.level.GameType.SPECTATOR
            else -> return false
        }
        server.execute {
            server.playerList.getPlayer(playerId)?.setGameMode(gameMode)
        }
        return true
    }

    fun hasLocalServer(client: Minecraft): Boolean = client.singleplayerServer != null

    fun runOnRenderThread(client: Minecraft, task: Runnable) {
        client.execute(task)
    }

    fun createLiteral(text: String): MutableComponent = Component.literal(text)

    fun formatText(text: MutableComponent, formatting: ChatFormatting): MutableComponent = text.withStyle(formatting)

    fun captureBlockEntity(world: net.minecraft.world.level.Level, pos: BlockPos): BlockEntityDataSnapshot? {
        val blockEntity = world.getBlockEntity(pos) ?: return null
        return BlockEntityDataSnapshot(blockEntity.saveWithFullMetadata(world.registryAccess()).copy())
    }

    fun applyBlockEntity(world: net.minecraft.world.level.Level, write: BlockWrite, suppressUpdates: Boolean = true) {
        world.setBlock(
            write.pos,
            write.state,
            BlockWriteUpdatePolicy.capabilityFlags(
                suppressUpdates = suppressUpdates,
                modernCallbacksAvailable = true,
            ),
        )
        val payload = write.blockEntityData
        if (payload == null) {
            world.removeBlockEntity(write.pos)
            val provider = write.state.block as? net.minecraft.world.level.block.EntityBlock ?: return
            val blockEntity = provider.newBlockEntity(write.pos, write.state) ?: return
            world.getChunk(write.pos.x shr 4, write.pos.z shr 4).setBlockEntity(blockEntity)
            blockEntity.setChanged()
            return
        }

        val restored = payload.nbt.copy()
        restored.putInt("x", write.pos.x)
        restored.putInt("y", write.pos.y)
        restored.putInt("z", write.pos.z)
        val blockEntity = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(write.pos, write.state, restored, world.registryAccess())
            ?: return
        world.removeBlockEntity(write.pos)
        world.getChunk(write.pos.x shr 4, write.pos.z shr 4).setBlockEntity(blockEntity)
        blockEntity.setChanged()
    }

    fun registerAxionPayloadChannel(
        id: CustomPacketPayload.Type<AxionPluginPayload>,
        codec: StreamCodec<RegistryFriendlyByteBuf, AxionPluginPayload>,
    ) {
        // NeoForge payload registration is deferred to the mod event bus.
    }

    fun registerAxionReceiver(
        id: CustomPacketPayload.Type<AxionPluginPayload>,
        handler: (AxionPluginPayload) -> Unit,
    ) {
        clientPayloadHandlers[id] = handler
    }

    fun registerPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar: PayloadRegistrar = event.registrar("1")
        registrar.playToServer(AxionPluginPayload.ID, AxionPluginPayload.CODEC) { _, _ -> }
    }

    fun consumeClientPayload(payload: AxionPluginPayload) {
        clientPayloadHandlers[AxionPluginPayload.ID]?.invoke(payload)
    }

    fun sendAxionPayload(payload: AxionPluginPayload) {
        // Optional channel: vanilla / non-Axion servers never negotiated it.
        // Swallow send failures instead of killing the connection (the
        // handshake state machine marks the server as non-Axion on timeout).
        runCatching { ClientPacketDistributor.sendToServer(payload) }
    }

    fun supportsChunkedPreview(): Boolean {
        if (ShaderPackCompat.shouldDisableDirectGpuPreview()) {
            logger.info("[Axion GPU] supportsChunkedPreview=false (shader-pack fallback)")
            return false
        }
        val hasWrite4 = dynamicUniformsWrite4 != null
        val hasWrite5 = dynamicUniformsWrite5 != null
        val supported = hasWrite4 || hasWrite5 || dynamicUniformsWriteAny.isNotEmpty()
        logger.info(
            "[Axion GPU] supportsChunkedPreview={} (dynWrite4={}, dynWrite5={})",
            supported, hasWrite4, hasWrite5,
        )
        return supported
    }

    fun renderChunkedPreview(
        sessionId: String,
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        surfaceClipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int,
        alpha: Int,
        scale: Float,
    ): Boolean {
        return try {
            if (ShaderPackCompat.shouldDisableDirectGpuPreview()) return false
            val session = ChunkedPreviewLifecycle.acquire(sessionId)
            session.setFromClipboard(clipboard, surfaceClipboard, origins, scale)
            session.render(context, color, alpha).handled
        } catch (t: Throwable) {
            logger.warn("[Axion GPU] renderChunkedPreview failed for session={} — falling back to CPU path", sessionId, t)
            false
        }
    }

    // Rendering helpers for 1.21.11
    override fun getBlockRenderManager(client: Any): Any {
        return (client as Minecraft).blockRenderer
    }

    override fun getBlockRenderType(state: BlockState): Any {
        return net.minecraft.client.renderer.ItemBlockRenderTypes.getMovingBlockRenderType(state)
    }

    override fun getRenderingSeed(state: BlockState, pos: Any): Long {
        return state.getSeed(pos as net.minecraft.core.BlockPos)
    }

    override fun matrixStackPush(stack: Any): Any {
        return (stack as com.mojang.blaze3d.vertex.PoseStack).pushPose()
    }

    override fun matrixStackPop(stack: Any) {
        (stack as com.mojang.blaze3d.vertex.PoseStack).popPose()
    }

    override fun blockRenderManagerGetModel(manager: Any, state: BlockState): Any {
        return (manager as net.minecraft.client.renderer.block.BlockRenderDispatcher).getBlockModel(state)
    }

    @Suppress("UNCHECKED_CAST")
    override fun blockRenderManagerRenderBlock(manager: Any, state: BlockState, pos: Any, world: Any, matrixStack: Any, consumer: Any, checkSides: Boolean, parts: List<Any>): Boolean {
        (manager as net.minecraft.client.renderer.block.BlockRenderDispatcher).renderBatched(
            state,
            pos as net.minecraft.core.BlockPos,
            world as net.minecraft.world.level.BlockAndTintGetter,
            matrixStack as com.mojang.blaze3d.vertex.PoseStack,
            consumer as com.mojang.blaze3d.vertex.VertexConsumer,
            checkSides,
            parts as List<net.minecraft.client.renderer.block.model.BlockModelPart>
        )
        return true
    }

    override fun blockRenderManagerRenderFluid(manager: Any, pos: Any, world: Any, consumer: Any, state: BlockState, fluidState: Any): Boolean {
        (manager as net.minecraft.client.renderer.block.BlockRenderDispatcher).renderLiquid(
            pos as net.minecraft.core.BlockPos,
            world as net.minecraft.world.level.BlockAndTintGetter,
            consumer as com.mojang.blaze3d.vertex.VertexConsumer,
            state,
            fluidState as net.minecraft.world.level.material.FluidState
        )
        return true
    }

    // Entity API helpers for 1.21.11
    override fun entityIsRemoved(entity: Any): Boolean {
        return (entity as net.minecraft.world.entity.Entity).isRemoved
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
        return (entity as net.minecraft.world.entity.Entity).yRot
    }

    override fun entityGetPitch(entity: Any): Float {
        return (entity as net.minecraft.world.entity.Entity).xRot
    }

    override fun entityGetPassengerList(entity: Any): List<Any> {
        return (entity as net.minecraft.world.entity.Entity).passengers
    }

    override fun entitySetUuid(entity: Any, uuid: java.util.UUID) {
        (entity as net.minecraft.world.entity.Entity).setUUID(uuid)
    }

    override fun entitySetPositionAndAngles(entity: Any, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        (entity as net.minecraft.world.entity.Entity).absSnapTo(x, y, z, yaw, pitch)
    }

    override fun entityRefreshPositionAndAngles(entity: Any) {
        val e = entity as net.minecraft.world.entity.Entity
        e.absSnapTo(e.x, e.y, e.z, e.yRot, e.xRot)
    }

    override fun entityUpdatePassengerPosition(entity: Any, passenger: Any) {
        (entity as net.minecraft.world.entity.Entity).positionRider(passenger as net.minecraft.world.entity.Entity)
    }

    override fun entityTypeLoadEntityWithPassengers(tag: CompoundTag, world: Any, spawnReason: Any, entityProcessor: (Any) -> Any): Any? {
        return net.minecraft.world.entity.EntityType.loadEntityRecursive(
            tag,
            world as net.minecraft.world.level.Level,
            spawnReason as net.minecraft.world.entity.EntitySpawnReason,
        ) { entity ->
            entityProcessor(entity) as? net.minecraft.world.entity.Entity
        }
    }

    override fun worldSpawnNewEntityAndPassengers(world: Any, entity: Any): Boolean {
        return (world as net.minecraft.server.level.ServerLevel).tryAddFreshEntityWithPassengers(entity as net.minecraft.world.entity.Entity)
    }

    override fun worldGetOtherEntities(world: Any, entity: Any, box: Any): List<Any> {
        return (world as net.minecraft.world.level.Level).getEntities(
            entity as net.minecraft.world.entity.Entity,
            box as net.minecraft.world.phys.AABB
        ) { true }
    }

    // Minecraft API helpers for 1.21.11
    override fun clientGetServer(client: Any): Any? {
        return (client as Minecraft).singleplayerServer
    }

    override fun clientGetWorldRegistryKey(client: Any): Any? {
        return (client as Minecraft).level?.dimension()
    }

    override fun serverExecute(server: Any, task: Runnable) {
        (server as net.minecraft.client.server.IntegratedServer).execute(task)
    }

    @Suppress("UNCHECKED_CAST")
    override fun serverGetWorld(server: Any, registryKey: Any): Any? {
        return (server as net.minecraft.client.server.IntegratedServer).getLevel(registryKey as net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>)
    }

    override fun playerSendMessage(player: Any, message: Any, overlay: Boolean) {
        (player as net.minecraft.world.entity.player.Player).displayClientMessage(message as net.minecraft.network.chat.Component, overlay)
    }

    // Direction/BlockState API helpers for 1.21.11
    override fun directionGetVector(direction: Any): Any {
        // Block-grid step vector (Vec3i), not the unit Vec3 - callers use it
        // for BlockPos offsets and protocol vectors.
        return (direction as net.minecraft.core.Direction).getUnitVec3i()
    }

    override fun blockStateStringify(state: BlockState): String {
        return net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state)
    }

    fun rawBlockStateId(state: BlockState): Int {
        return Block.getId(state)
    }

    // Registry/BlockStateParser API helpers for 1.21.11
    override fun worldGetRegistryManager(world: Any): Any {
        return (world as net.minecraft.world.level.Level).registryAccess()
    }

    override fun blockArgumentParserBlock(registry: Any, state: String): Any {
        return net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
            (registry as RegistryAccess).lookupOrThrow(Registries.BLOCK),
            state,
            false
        )
    }

    fun closeChunkedPreviews() {
        ChunkedPreviewLifecycle.closeAll()
    }

    fun drawMultipleIndexedPreview(
        pass: RenderPass,
        drawList: List<SectionDrawEntry>,
        uniformSlices: List<GpuBufferSlice>,
    ): Boolean {
        if (drawList.isEmpty()) return false
        return try {
            doDrawMultipleIndexed(pass, drawList, uniformSlices)
        } catch (_: LinkageError) {
            logger.info("[Axion GPU] drawMultipleIndexed not available (API mismatch), using per-section draw loop")
            false
        } catch (e: Exception) {
            logger.warn("[Axion GPU] drawMultipleIndexed failed at runtime, using per-section draw loop", e)
            false
        }
    }

    private fun doDrawMultipleIndexed(
        pass: RenderPass,
        drawList: List<SectionDrawEntry>,
        uniformSlices: List<GpuBufferSlice>,
    ): Boolean {
        val renderObjects = ArrayList<RenderPass.Draw<Unit>>(drawList.size)
        for (i in drawList.indices) {
            val entry = drawList[i]
            val vb = entry.buffer.vertexBufferGpu ?: return false
            val ib = entry.buffer.indexBufferGpu ?: return false
            val slice = uniformSlices[i]
            renderObjects += RenderPass.Draw<Unit>(
                0, vb, ib, entry.indexType, 0, entry.indexCount,
                java.util.function.BiConsumer { _, uploader ->
                    uploader.upload("DynamicTransforms", slice)
                },
            )
        }
        val first = renderObjects[0]
        pass.drawMultipleIndexed(
            renderObjects,
            first.indexBuffer(),
            first.indexType(),
            listOf("DynamicTransforms"),
            Unit,
        )
        return true
    }

    // Rendering compatibility for 1.21.11
    private const val BLOCK_ATLAS_SAMPLER_NAME: String = "Sampler0"
    private var loggedAtlasResult: Boolean = false
    private var loggedBindFailure: Boolean = false

    fun getBlockAtlasTextureView(client: Minecraft): GpuTextureView? {
        return try {
            val atlas = client.atlasManager?.getAtlasOrThrow(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "blocks"))
            currentAtlasSampler = blockAtlasSampler(atlas)
            val view = atlas?.getTextureView()
            if (!loggedAtlasResult) {
                loggedAtlasResult = true
                logger.info("[Axion GPU] Atlas lookup: view={}, sampler={}", view != null, currentAtlasSampler != null)
            }
            view
        } catch (e: Exception) {
            if (!loggedAtlasResult) {
                loggedAtlasResult = true
                logger.warn("[Axion GPU] Atlas lookup failed", e)
            }
            currentAtlasSampler = null
            null
        }
    }

    /**
     * Sampler for the preview's block atlas binding.
     *
     * Prefer the sampler vanilla terrain uses (clamped, mipmapped, LINEAR
     * minification with NEAREST magnification) so preview blocks filter exactly
     * like the world blocks around them; the atlas' own sampler is unmipmapped
     * and only serves as a fallback if that field ever moves.
     */
    private fun blockAtlasSampler(atlas: AbstractTexture?): GpuSampler? {
        return runCatching { RenderTypes.MOVING_BLOCK_SAMPLER.get() }.getOrNull()
            ?: runCatching { atlas?.sampler }.getOrNull()
    }

    /** The lightmap is read with texelFetch, so it wants plain unmipmapped NEAREST. */
    private fun lightmapSampler(): GpuSampler? = runCatching {
        RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST)
    }.getOrNull()

    fun bindTextureToRenderPass(pass: RenderPass, samplerName: String, textureView: GpuTextureView) {
        // 1.21.11 replaced RenderPass.bindSampler(name, view) with
        // bindTexture(name, view, sampler): there is no name-only binding left,
        // so an unresolved sampler means the draw samples whatever sampler state
        // the previous pass left on that texture unit.
        val sampler = if (samplerName == BLOCK_ATLAS_SAMPLER_NAME) currentAtlasSampler else lightmapSampler()
        if (sampler == null) {
            if (!loggedBindFailure) {
                loggedBindFailure = true
                logger.error("[Axion GPU] No block-atlas sampler resolved — GPU preview textures will be misfiltered")
            }
            return
        }
        try {
            pass.bindTexture(samplerName, textureView, sampler)
        } catch (e: Exception) {
            logger.warn("[Axion GPU] bindTexture failed for {}", samplerName, e)
        }
    }

    fun getRenderPipeline(layer: RenderType): RenderPipeline? {
        return try {
            layer.pipeline()
        } catch (_: Throwable) {
            // renderPipeline property may not exist on 1.21.9 — try reflection
            try {
                val method = layer.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 && RenderPipeline::class.java.isAssignableFrom(it.returnType)
                }
                method?.invoke(layer) as? RenderPipeline
            } catch (_: Throwable) {
                null
            }
        }
    }

    private var loggedPipelineCreation = false
    private val previewDepthTest = if (PreviewVisualPolicy.XRAY_BLOCK_PREVIEWS) {
        DepthTestFunction.NO_DEPTH_TEST
    } else {
        DepthTestFunction.LEQUAL_DEPTH_TEST
    }

    fun getPreviewShellPipeline(
        vertexFormat: VertexFormat,
        drawMode: VertexFormat.Mode,
        ignoreTextureAlpha: Boolean = true,
    ): RenderPipeline? {
        return try {
            previewShellPipelines.computeIfAbsent(drawMode to ignoreTextureAlpha) {
                var builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("axion", "preview_shell"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("axion", "core/preview_shell"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("axion", "core/preview_shell"))
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(previewDepthTest)
                    // END_MAIN runs before clouds. Writing only the preview's
                    // surface depth keeps later cloud pixels out of the ghost
                    // while leaving the rest of the cloud pass untouched.
                    .withDepthWrite(true)
                    .withCull(PreviewVisualPolicy.CULL_GHOST_BACK_FACES)
                    .withVertexFormat(vertexFormat, drawMode)
                if (ignoreTextureAlpha) {
                    // Destination ghosts take opacity from the modulator alone
                    // (PreviewVisualPolicy.DESTINATION_ALPHA). Without this
                    // define a translucent texel alpha (~0.4 for glass)
                    // compounds in and the ghost reads as a faint shell.
                    builder = builder.withShaderDefine(PreviewVisualPolicy.IGNORE_TEXTURE_ALPHA_DEFINE)
                }
                builder.build()
            }
        } catch (t: Throwable) {
            if (!loggedPipelineCreation) {
                loggedPipelineCreation = true
                logger.warn("[Axion GPU] Custom preview pipeline creation failed (1.21.9?), will use render layer pipeline", t)
            }
            null
        }
    }

    fun writeDynamicUniforms(
        dynamicUniforms: net.minecraft.client.renderer.DynamicUniforms,
        mvMatrix: Matrix4fc,
        colorTint: Vector4fc,
        zeroVec: Vector3fc,
        normalMatrix: Matrix4fc,
        lineWidth: Float
    ): GpuBufferSlice {
        val write4 = dynamicUniformsWrite4
        if (write4 != null) {
            return write4.invoke(dynamicUniforms, mvMatrix, colorTint, zeroVec, normalMatrix) as GpuBufferSlice
        }

        val write5 = dynamicUniformsWrite5
        if (write5 != null) {
            return write5.invoke(dynamicUniforms, mvMatrix, colorTint, zeroVec, normalMatrix, lineWidth) as GpuBufferSlice
        }

        // Final fallback: try any method returning GpuBufferSlice, adapting arg count
        val candidates = dynamicUniformsWriteAny
        for (candidate in candidates) {
            try {
                val args: Array<Any?> = when (candidate.parameterTypes.size) {
                    4 -> arrayOf(mvMatrix, colorTint, zeroVec, normalMatrix)
                    5 -> arrayOf(mvMatrix, colorTint, zeroVec, normalMatrix, lineWidth)
                    6 -> arrayOf(mvMatrix, colorTint, zeroVec, normalMatrix, lineWidth, 0f)
                    3 -> arrayOf(mvMatrix, colorTint, normalMatrix)
                    else -> continue
                }
                return candidate.invoke(dynamicUniforms, *args) as GpuBufferSlice
            } catch (_: Exception) {
                continue
            }
        }

        throw NoSuchMethodError("No compatible DynamicUniforms.write overload found (write4 and write5 both null, candidates=${candidates.size})")
    }

    fun playSoundClient(
        world: net.minecraft.client.multiplayer.ClientLevel,
        x: Double,
        y: Double,
        z: Double,
        sound: net.minecraft.sounds.SoundEvent,
        soundCategory: net.minecraft.sounds.SoundSource,
        volume: Float,
        pitch: Float
    ) {
        world.playSound(null as net.minecraft.world.entity.Entity?, x, y, z, sound, soundCategory, volume, pitch)
    }

    fun getMainInventoryStacks(inventory: net.minecraft.world.entity.player.Inventory): List<ItemStack> {
        return inventory.nonEquipmentItems
    }

    fun getScaledMouseX(client: Minecraft): Double {
        return client.mouseHandler.getScaledXPos(client.window)
    }

    fun getScaledMouseY(client: Minecraft): Double {
        return client.mouseHandler.getScaledYPos(client.window)
    }

    fun registerHudElements(
        hudId: Identifier,
        hintHudId: Identifier,
        hudRenderer: (GuiGraphics, DeltaTracker) -> Unit,
        hintRenderer: (GuiGraphics, DeltaTracker) -> Unit,
    ) {
        this.hudRenderer = hudRenderer
        this.hintHudRenderer = hintRenderer
    }

    fun renderHud(event: RenderGuiLayerEvent.Post) {
        val context = event.guiGraphics
        val tickCounter = Minecraft.getInstance().deltaTracker
        hudRenderer?.invoke(context, tickCounter)
        hintHudRenderer?.invoke(context, tickCounter)
    }

    fun captureEntityData(entity: Entity): CompoundTag? {
        val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess())
        return if (entity.saveAsPassenger(output)) output.buildResult() else null
    }

    fun drawGuiTexture(
        context: GuiGraphics,
        texture: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height)
    }

    fun renderVanillaButton(
        context: GuiGraphics,
        button: net.minecraft.client.gui.components.Button,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        button.render(context, mouseX, mouseY, delta)
    }

    @Suppress("UNUSED_PARAMETER")
    fun clickVanillaButton(
        client: Minecraft,
        button: net.minecraft.client.gui.components.Button,
        mouseX: Double,
        mouseY: Double,
        mouseButton: Int,
    ): Boolean = button.mouseClicked(
        net.minecraft.client.input.MouseButtonEvent(
            mouseX,
            mouseY,
            net.minecraft.client.input.MouseButtonInfo(mouseButton, 0),
        ),
        false,
    )

    fun drawGuiTextureRegion(
        context: GuiGraphics,
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
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u.toFloat(), v.toFloat(), width, height, textureWidth, textureHeight)
    }

    private val cameraPosField: Field? by lazy {
        try {
            Camera::class.java.getDeclaredField("pos").apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            Camera::class.java.declaredFields.firstOrNull { it.type == Vec3::class.java }
                ?.apply { isAccessible = true }
        }
    }

    private val cameraPosMethod: java.lang.reflect.Method? by lazy {
        Camera::class.java.methods.firstOrNull { it.name == "getPos" && it.parameterCount == 0 }
            ?: Camera::class.java.methods.firstOrNull {
                it.parameterCount == 0 && it.returnType == Vec3::class.java
            }
    }

    fun getCameraPos(camera: Camera): Vec3 {
        cameraPosMethod?.let { m ->
            try { return m.invoke(camera) as Vec3 } catch (_: Exception) {}
        }

        cameraPosField?.let { f ->
            try { return f.get(camera) as Vec3 } catch (_: Exception) {}
        }

        throw IllegalStateException("Cannot access camera position — no method or field found on Camera class")
    }

    // ItemStack codec helpers for hotbar save/load (1.21.11 uses reflection directly)
    override fun itemStackEncode(registryManager: Any, stack: Any): ByteArray? {
        return runCatching {
            val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), registryManager as RegistryAccess)
            ItemStack.STREAM_CODEC.encode(buf, stack as ItemStack)
            ByteArray(buf.readableBytes()).also { buf.getBytes(0, it) }
        }.getOrNull()
    }

    override fun itemStackDecode(registryManager: Any, bytes: ByteArray): Any? {
        return runCatching {
            val buf = RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), registryManager as RegistryAccess)
            ItemStack.STREAM_CODEC.decode(buf)
        }.getOrNull()
    }

    override fun createAxionPluginPayloadCodec(): Any {
        // 1.21.11 StreamCodec API - try ofStatic with 2 parameters, fallback to any 2-parameter static method
        val codecClass = StreamCodec::class.java

        // Try ofStatic first
        val method = codecClass.methods.firstOrNull { it.name == "ofStatic" && it.parameterCount == 2 }
            // Fallback: try any static method with 2 parameters that returns StreamCodec
            ?: codecClass.methods.firstOrNull {
                it.parameterCount == 2 &&
                it.returnType == codecClass &&
                java.lang.reflect.Modifier.isStatic(it.modifiers)
            }
            ?: throw NoSuchMethodError("No compatible StreamCodec factory method found in 1.21.11")

        val encoderType = method.parameterTypes[0]
        val decoderType = method.parameterTypes[1]

        val encoder = java.lang.reflect.Proxy.newProxyInstance(encoderType.classLoader, arrayOf(encoderType)) { _, method, args ->
            if (method.name == "encode" && args != null && args.size == 2) {
                // Encoder-style factories call encode(value, buffer); raw
                // StreamCodec.encode is (buffer, value). Detect by type.
                if (args[0] is AxionPluginPayload) {
                    (args[1] as RegistryFriendlyByteBuf).writeBytes((args[0] as AxionPluginPayload).bytes)
                } else {
                    (args[0] as RegistryFriendlyByteBuf).writeBytes((args[1] as AxionPluginPayload).bytes)
                }
            }
            null
        }

        val decoder = java.lang.reflect.Proxy.newProxyInstance(decoderType.classLoader, arrayOf(decoderType)) { _, method, args ->
            if (method.name == "decode" && args != null && args.size == 1) {
                val buf = args[0] as RegistryFriendlyByteBuf
                val bytes = ByteArray(buf.readableBytes())
                buf.readBytes(bytes)
                AxionPluginPayload(bytes)
            } else {
                null
            }
        }

        return method.invoke(null, encoder, decoder)
    }
}

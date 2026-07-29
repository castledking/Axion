package axion.client.compat

import axion.client.render.AxionWorldRenderContext
import axion.client.render.PreviewVisualPolicy
import axion.client.render.RenderLayerCompat
import axion.client.render.ShaderPackCompat
import axion.client.render.gpu.ChunkedPreviewLifecycle
import axion.client.render.gpu.SectionDrawEntry
import axion.common.model.ClipboardBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.block.Block
import net.minecraft.commands.arguments.blocks.BlockStateParser
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
import com.mojang.brigadier.StringReader
import net.minecraft.text.Text
import net.minecraft.text.MutableText
import net.minecraft.util.Formatting
import axion.client.config.formatted
import axion.client.network.BlockWrite
import axion.client.network.BlockWriteUpdatePolicy
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
import net.minecraft.network.codec.StreamCodec
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.EntityProcessor
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import org.lwjgl.glfw.GLFW
import axion.common.compat.VersionCompat
import java.util.concurrent.ConcurrentHashMap

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
        return BlockStateParser.serialize(state)
    }

    override fun stringToBlockState(str: String): BlockState? {
        val world = MinecraftClient.getInstance().level ?: return null
        return try {
            BlockStateParser.parseForBlock(
                world.registryAccess().lookupOrThrow(RegistryKeys.BLOCK),
                StringReader(str),
                true,
            ).blockState()
        } catch (_: Exception) {
            null
        }
    }

    override fun itemStackToNbt(stack: ItemStack): NbtCompound {
        return NbtCompound()
    }

    override fun nbtToItemStack(nbt: NbtCompound): ItemStack {
        return ItemStack.EMPTY
    }

    override fun shouldUseNonConsumingKeybind(): Boolean = false

    fun supportsChunkedPreview(): Boolean = !ShaderPackCompat.shouldDisableDirectGpuPreview()

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
        if (ShaderPackCompat.shouldDisableDirectGpuPreview()) return false
        val session = ChunkedPreviewLifecycle.acquire(sessionId)
        session.setFromClipboard(clipboard, surfaceClipboard, origins)
        return session.render(context, color, alpha).handled
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
        val draws = ArrayList<RenderPass.Draw<Unit>>(drawList.size)
        for (i in drawList.indices) {
            val entry = drawList[i]
            val vb = entry.buffer.vertexBufferGpu ?: return false
            val ib = entry.buffer.indexBufferGpu ?: return false
            val slice = uniformSlices[i]
            draws += RenderPass.Draw<Unit>(
                0,
                vb,
                ib,
                entry.indexType,
                0,
                entry.indexCount,
                0,
                java.util.function.BiConsumer { _, uploader ->
                    uploader.upload("DynamicTransforms", slice)
                },
            )
        }
        val first = draws[0]
        pass.drawMultipleIndexed(draws, first.indexBuffer(), first.indexType(), listOf("DynamicTransforms"), Unit)
        return true
    }

    fun writeDynamicUniforms(
        dynamicUniforms: net.minecraft.client.gl.DynamicUniforms,
        mvMatrix: org.joml.Matrix4fc,
        colorTint: org.joml.Vector4fc,
        zeroVec: org.joml.Vector3fc,
        normalMatrix: org.joml.Matrix4fc,
        lineWidth: Float,
    ): GpuBufferSlice {
        return dynamicUniforms.writeTransform(mvMatrix, colorTint, zeroVec, normalMatrix)
    }

    private var currentAtlasSampler: com.mojang.blaze3d.textures.GpuSampler? = null

    fun getBlockAtlasTextureView(client: MinecraftClient): GpuTextureView? {
        return try {
            val atlas = client.atlasManager.getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS)
            currentAtlasSampler = atlas.sampler
            atlas.textureView
        } catch (_: Exception) {
            null
        }
    }

    fun bindTextureToRenderPass(pass: RenderPass, samplerName: String, textureView: GpuTextureView) {
        val sampler = currentAtlasSampler ?: return
        pass.bindTexture(samplerName, textureView, sampler)
    }

    fun getRenderPipeline(layer: RenderLayer): RenderPipeline {
        return layer.pipeline()
    }

    private val previewShellPipelines = ConcurrentHashMap<net.minecraft.client.render.DrawMode, RenderPipeline>()
    private val previewDepthState = if (PreviewVisualPolicy.XRAY_BLOCK_PREVIEWS) {
        DepthStencilState(CompareOp.ALWAYS_PASS, false, 0.0f, 0.0f)
    } else {
        // Same camera-facing offset as the old GL polygon offset, expressed in
        // the pipeline so OpenGL and every future backend agree.
        DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0f, -1.0f)
    }
    private val bufferedPreviewShellLayers = ConcurrentHashMap<RenderLayer, RenderLayer>()
    private val blockAtlasSampler = java.util.function.Supplier {
        RenderSystem.getSamplerCache().getSampler(
            AddressMode.CLAMP_TO_EDGE,
            AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR,
            FilterMode.NEAREST,
            true,
        )
    }

    fun getPreviewShellPipeline(
        vertexFormat: VertexFormat,
        drawMode: net.minecraft.client.render.DrawMode,
    ): RenderPipeline {
        return previewShellPipelines.computeIfAbsent(drawMode) {
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("axion", "pipeline/preview_shell"))
                .withVertexShader(Identifier.fromNamespaceAndPath("axion", "core/preview_shell"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("axion", "core/preview_shell"))
                .withSampler("Sampler0")
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                // Read scene depth so foreground terrain hides the translucent
                // preview. Depth writes remain off so the preview never hides
                // later world/UI rendering. XRAY_BLOCK_PREVIEWS is the explicit
                // opt-in escape hatch for the old through-wall behavior.
                .withDepthStencilState(previewDepthState)
                .withCull(PreviewVisualPolicy.CULL_GHOST_BACK_FACES)
                .withVertexFormat(vertexFormat, drawMode)
                .build()
        }
    }

    /**
     * Buffered counterpart to the direct preview pipeline.
     *
     * CPU fallback tessellation still needs a RenderLayer so Minecraft can batch
     * and submit its vertices. Wrapping the existing preview pipeline keeps the
     * same no-cull/depth-policy/no-depth-write contract while RenderSetup supplies
     * the block atlas and vanilla's translucent upload sorting.
     */
    fun getBufferedPreviewShellLayer(baseLayer: RenderLayer): RenderLayer {
        // Iris only maps vanilla RenderLayer instances to a shader-pack
        // program. Preserve the known-good vanilla fallback while a pack is
        // active; the custom layer is for Minecraft's normal renderer.
        if (ShaderPackCompat.isShaderPackActive()) return baseLayer
        return bufferedPreviewShellLayers.computeIfAbsent(baseLayer) { layer ->
            val setup = RenderSetup.builder(
                getPreviewShellPipeline(layer.format(), layer.mode()),
            )
                .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS, blockAtlasSampler)
                .sortOnUpload()
                .createRenderSetup()
            RenderLayerCompat.createPipelineLayer("axion_preview_shell_cpu", setup)
        }
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

    fun sendGameModeCommand(client: MinecraftClient, gameModeId: String) {
        client.connection?.sendCommand("gamemode $gameModeId")
    }

    fun changeLocalGameMode(client: MinecraftClient, gameModeId: String): Boolean {
        val server = client.getSingleplayerServer() ?: return false
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
        world.setBlock(write.pos, write.state, BlockWriteUpdatePolicy.MODERN_NO_PHYSICS_FLAGS)
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

    override fun getBlockRenderManager(client: Any): Any {
        val minecraft = client as MinecraftClient
        return net.minecraft.client.render.BlockRenderManager(true, true, minecraft.blockColors)
    }

    override fun getBlockRenderType(state: BlockState): Any {
        return state.renderShape
    }

    override fun getRenderingSeed(state: BlockState, pos: Any): Long {
        return state.getSeed(pos as BlockPos)
    }

    override fun matrixStackPush(stack: Any): Any {
        (stack as com.mojang.blaze3d.vertex.PoseStack).pushPose()
        return stack
    }

    override fun matrixStackPop(stack: Any) {
        (stack as com.mojang.blaze3d.vertex.PoseStack).popPose()
    }

    override fun blockRenderManagerGetModel(manager: Any, state: BlockState): Any {
        return MinecraftClient.getInstance().modelManager.blockStateModelSet.get(state)
    }

    override fun blockRenderManagerRenderBlock(manager: Any, state: BlockState, pos: Any, world: Any, matrixStack: Any, consumer: Any, checkSides: Boolean, parts: List<Any>): Boolean {
        val blockPos = pos as BlockPos
        val renderer = manager as net.minecraft.client.render.BlockRenderManager
        val model = MinecraftClient.getInstance().modelManager.blockStateModelSet.get(state)
        val output = net.minecraft.client.renderer.block.BlockQuadOutput {
                x: Float,
                y: Float,
                z: Float,
                quad: net.minecraft.client.resources.model.geometry.BakedQuad,
                quadInstance: com.mojang.blaze3d.vertex.QuadInstance,
            ->
            (consumer as com.mojang.blaze3d.vertex.VertexConsumer).putBlockBakedQuad(x, y, z, quad, quadInstance)
        }
        renderer.tesselateBlock(
            output,
            blockPos.x.toFloat(),
            blockPos.y.toFloat(),
            blockPos.z.toFloat(),
            world as net.minecraft.client.renderer.block.BlockAndTintGetter,
            blockPos,
            state,
            model,
            state.getSeed(blockPos),
        )
        return true
    }

    override fun blockRenderManagerRenderFluid(manager: Any, pos: Any, world: Any, consumer: Any, state: BlockState, fluidState: Any): Boolean {
        return false
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

    override fun entitySetPositionAndAngles(entity: Any, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        val e = entity as net.minecraft.world.entity.Entity
        e.setPos(x, y, z)
        e.setYRot(yaw)
        e.setXRot(pitch)
        e.setOldPosAndRot()
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
        val level = world as net.minecraft.server.level.ServerLevel
        val input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag)
        return EntityType.loadEntityRecursive(
            input,
            level,
            spawnReason as net.minecraft.world.entity.EntitySpawnReason,
            EntityProcessor { entity -> entityProcessor(entity) as net.minecraft.world.entity.Entity },
        )
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
        return BlockStateParser.serialize(state)
    }

    fun rawBlockStateId(state: BlockState): Int {
        return net.minecraft.world.level.block.Block.getId(state)
    }

    // Registry/BlockArgumentParser API helpers for 26.1.2
    override fun worldGetRegistryManager(world: Any): Any {
        return (world as net.minecraft.world.level.Level).registryAccess()
    }

    @Suppress("UNCHECKED_CAST")
    override fun blockArgumentParserBlock(registry: Any, state: String): Any {
        return BlockStateParser.parseForBlock(
            registry as net.minecraft.core.HolderLookup<net.minecraft.world.level.block.Block>,
            StringReader(state),
            true,
        )
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
                handler(payload)
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
        val hotbarKey = net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
            hotbarKey,
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

    fun captureEntityData(entity: Entity): NbtCompound? {
        val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.level().registryAccess())
        return if (entity.save(output)) {
            output.buildResult()
        } else {
            null
        }
    }

    fun drawGuiTexture(
        context: DrawContext,
        texture: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        context.delegate.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height)
    }

    fun renderVanillaButton(
        context: DrawContext,
        button: net.minecraft.client.gui.widget.ButtonWidget,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        button.extractRenderState(context.delegate, mouseX, mouseY, delta)
    }

    @Suppress("UNUSED_PARAMETER")
    fun clickVanillaButton(
        client: MinecraftClient,
        button: net.minecraft.client.gui.widget.ButtonWidget,
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
        context.delegate.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u.toFloat(), v.toFloat(), width, height, textureWidth, textureHeight)
    }

    fun getCameraPos(camera: Camera): Vec3d {
        return camera.position()
    }

    // ItemStack codec helper for 26.1.x
    override fun itemStackEncode(registryManager: Any, stack: Any): ByteArray? {
        return runCatching {
            val buf = RegistryByteBuf(Unpooled.buffer(), registryManager as net.minecraft.registry.DynamicRegistryManager)
            itemStackStreamCodec().encode(buf, stack as ItemStack)
            val bytes = ByteArray(buf.readableBytes())
            buf.getBytes(0, bytes)
            bytes
        }.getOrNull()
    }

    override fun itemStackDecode(registryManager: Any, bytes: ByteArray): Any? {
        return runCatching {
            val buf = RegistryByteBuf(Unpooled.wrappedBuffer(bytes), registryManager as net.minecraft.registry.DynamicRegistryManager)
            itemStackStreamCodec().decode(buf)
        }.getOrNull()
    }

    private fun itemStackStreamCodec(): StreamCodec<RegistryByteBuf, ItemStack> {
        return ItemStack.STREAM_CODEC
    }

    override fun createAxionPluginPayloadCodec(): Any {
        // 26.1.x aliases PacketCodec to StreamCodec, whose factory is named of().
        val codecClass = PacketCodec::class.java
        val method = codecClass.methods.firstOrNull { it.name == "ofStatic" && it.parameterCount == 2 }
            ?: codecClass.methods.firstOrNull { it.name == "of" && it.parameterCount == 2 }
            ?: codecClass.methods.firstOrNull {
                it.parameterCount == 2 &&
                    it.returnType == codecClass &&
                    java.lang.reflect.Modifier.isStatic(it.modifiers)
            }
            ?: throw NoSuchMethodError("No compatible PacketCodec/StreamCodec factory method found in 26.1.x")
        val encoderType = method.parameterTypes[0]
        val decoderType = method.parameterTypes[1]

        val encoder = java.lang.reflect.Proxy.newProxyInstance(encoderType.classLoader, arrayOf(encoderType)) { _, method, args ->
            if (method.name == "encode" && args != null && args.size == 2) {
                val buf = args[0] as RegistryByteBuf
                val payload = args[1] as AxionPluginPayload
                buf.writeBytes(payload.bytes)
            }
            null
        }

        val decoder = java.lang.reflect.Proxy.newProxyInstance(decoderType.classLoader, arrayOf(decoderType)) { _, method, args ->
            if (method.name == "decode" && args != null && args.size == 1) {
                val buf = args[0] as RegistryByteBuf
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

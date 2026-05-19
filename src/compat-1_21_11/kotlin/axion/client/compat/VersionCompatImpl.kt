package axion.client.compat

import axion.client.render.AxionWorldRenderContext
import axion.client.render.ShaderPackCompat
import axion.client.render.gpu.ChunkedPreviewLifecycle
import axion.client.render.gpu.SectionDrawEntry
import axion.client.network.AxionPluginPayload
import axion.client.network.BlockWrite
import axion.common.compat.VersionCompat
import java.lang.reflect.Field
import axion.common.model.BlockEntityDataSnapshot
import axion.common.model.ClipboardBuffer
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.textures.GpuTextureView
import io.netty.buffer.Unpooled
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.Camera
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderTickCounter
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
import net.minecraft.storage.NbtWriteView
import net.minecraft.util.ErrorReporter
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.text.Text
import net.minecraft.text.MutableText
import net.minecraft.util.Formatting
import org.joml.Matrix4fc
import org.joml.Vector3fc
import org.joml.Vector4fc
import org.slf4j.LoggerFactory

/**
 * Version compatibility implementation for Minecraft 1.21.11
 */
object VersionCompatImpl : VersionCompat {
    private val logger = LoggerFactory.getLogger(VersionCompatImpl::class.java)
    private var currentAtlasSampler: Any? = null
    private val previewShellPipelines = java.util.EnumMap<VertexFormat.DrawMode, RenderPipeline>(VertexFormat.DrawMode::class.java)

    private val dynamicUniformsWrite4 by lazy {
        net.minecraft.client.gl.DynamicUniforms::class.java.methods.firstOrNull { method ->
            method.parameterTypes.size == 4 &&
                GpuBufferSlice::class.java.isAssignableFrom(method.returnType) &&
                method.name != "equals" && method.name != "toString" && method.name != "hashCode"
        }.also {
            if (it != null) logger.info("[Axion GPU] Found DynamicUniforms.write(4-arg): {}", it.name)
        }
    }

    private val dynamicUniformsWrite5 by lazy {
        net.minecraft.client.gl.DynamicUniforms::class.java.methods.firstOrNull { method ->
            method.parameterTypes.size == 5 &&
                GpuBufferSlice::class.java.isAssignableFrom(method.returnType) &&
                method.name != "equals" && method.name != "toString" && method.name != "hashCode"
        }.also {
            if (it != null) logger.info("[Axion GPU] Found DynamicUniforms.write(5-arg): {}", it.name)
        }
    }

    private val dynamicUniformsWriteAny by lazy {
        // Last resort: find ANY method returning GpuBufferSlice
        net.minecraft.client.gl.DynamicUniforms::class.java.methods.filter { method ->
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
                net.minecraft.client.gl.DynamicUniforms::class.java.methods.forEach { m ->
                    logger.warn("  {}({}) -> {}", m.name, m.parameterTypes.joinToString { it.simpleName }, m.returnType.simpleName)
                }
            }
        }
    }

    private val renderPassBindTexture by lazy {
        // 1.21.11: bindTexture(String, GpuTextureView, GpuSampler) — 3 params
        RenderPass::class.java.methods.firstOrNull { method ->
            method.name == "bindTexture" && method.parameterTypes.size == 3
        }.also { m ->
            if (m != null) {
                logger.info("[Axion GPU] Found RenderPass.bindTexture(3-arg): {}", m)
            }
        }
    }

    private val renderPassBindSampler by lazy {
        // 1.21.9/1.21.10: bindSampler(String, GpuTextureView) — 2 params
        RenderPass::class.java.methods.firstOrNull { method ->
            method.name == "bindSampler" && method.parameterTypes.size == 2
        }.also { m ->
            if (m != null) {
                logger.info("[Axion GPU] Found RenderPass.bindSampler(2-arg): {}", m)
            } else {
                // Dump all RenderPass methods for diagnosis if neither primary lookup matched
                val allMethods = RenderPass::class.java.methods.map { "${it.name}(${it.parameterTypes.joinToString { t -> t.simpleName }})" }
                logger.warn("[Axion GPU] RenderPass.bindSampler(2-arg) NOT found. Available methods: {}", allMethods)
            }
        }
    }

    private fun getRegistryManager(): DynamicRegistryManager? {
        return MinecraftClient.getInstance().world?.registryManager
    }

    private fun getRegistryOps(): com.mojang.serialization.DynamicOps<NbtElement>? {
        val registryManager = getRegistryManager() ?: return null
        return RegistryOps.of(NbtOps.INSTANCE, registryManager)
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
        val nbt = NbtCompound()
        val ops = getRegistryOps() ?: return nbt
        nbt.copyFromCodec(ItemStack.MAP_CODEC, ops, stack)
        return nbt
    }

    override fun nbtToItemStack(nbt: NbtCompound): ItemStack {
        val ops = getRegistryOps() ?: return ItemStack.EMPTY
        return nbt.decode(ItemStack.MAP_CODEC, ops).orElse(ItemStack.EMPTY)
    }

    override fun shouldUseNonConsumingKeybind(): Boolean {
        // 1.21.8+ handles keybind conflicts properly with wasPressed()
        return false
    }

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

    fun getModVersion(modId: String): String {
        return FabricLoader.getInstance()
            .getModContainer(modId)
            .orElseThrow()
            .metadata
            .version
            .friendlyString
    }

    fun notifyPlayer(player: net.minecraft.client.network.ClientPlayerEntity?, text: Text, overlay: Boolean) {
        player?.sendMessage(text, overlay)
    }

    fun hasLocalServer(client: MinecraftClient): Boolean = client.server != null

    fun runOnRenderThread(client: MinecraftClient, task: Runnable) {
        client.execute(task)
    }

    fun createLiteral(text: String): MutableText = Text.literal(text)

    fun formatText(text: MutableText, formatting: Formatting): MutableText = text.formatted(formatting)

    fun captureBlockEntity(world: net.minecraft.world.World, pos: BlockPos): BlockEntityDataSnapshot? {
        val blockEntity = world.getBlockEntity(pos) ?: return null
        return BlockEntityDataSnapshot(blockEntity.createNbtWithIdentifyingData(world.registryManager).copy())
    }

    fun applyBlockEntity(world: net.minecraft.world.World, write: BlockWrite) {
        world.setBlockState(write.pos, write.state, 3)
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

    fun registerAxionPayloadChannel(
        id: CustomPayload.Id<AxionPluginPayload>,
        codec: PacketCodec<RegistryByteBuf, AxionPluginPayload>,
    ) {
        PayloadTypeRegistry.playC2S().register(id, codec)
        PayloadTypeRegistry.playS2C().register(id, codec)
    }

    fun registerAxionReceiver(
        id: CustomPayload.Id<AxionPluginPayload>,
        handler: (AxionPluginPayload) -> Unit,
    ) {
        ClientPlayNetworking.registerGlobalReceiver(id) { payload, context ->
            context.client().execute {
                handler(payload)
            }
        }
    }

    fun sendAxionPayload(payload: AxionPluginPayload) {
        ClientPlayNetworking.send(payload)
    }

    fun supportsChunkedPreview(): Boolean {
        if (ShaderPackCompat.shouldDisableDirectGpuPreview()) {
            logger.info("[Axion GPU] supportsChunkedPreview=false (shader-pack fallback)")
            return false
        }
        val hasBindTexture = renderPassBindTexture != null
        val hasBindSampler = renderPassBindSampler != null
        val hasWrite4 = dynamicUniformsWrite4 != null
        val hasWrite5 = dynamicUniformsWrite5 != null
        val supported = hasBindTexture || hasBindSampler
        logger.info(
            "[Axion GPU] supportsChunkedPreview={} (bindTexture={}, bindSampler={}, dynWrite4={}, dynWrite5={})",
            supported, hasBindTexture, hasBindSampler, hasWrite4, hasWrite5,
        )
        return supported
    }

    fun renderChunkedPreview(
        sessionId: String,
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int,
        alpha: Int,
    ): Boolean {
        return try {
            val session = ChunkedPreviewLifecycle.acquire(sessionId)
            session.setFromClipboard(clipboard, origins)
            session.render(context, color, alpha).handled
        } catch (t: Throwable) {
            logger.warn("[Axion GPU] renderChunkedPreview failed for session={} — falling back to CPU path", sessionId, t)
            false
        }
    }

    // Rendering helpers for 1.21.11
    override fun getBlockRenderManager(client: Any): Any {
        return (client as MinecraftClient).blockRenderManager
    }

    override fun getBlockRenderType(state: BlockState): Any {
        return state.renderType
    }

    override fun getRenderingSeed(state: BlockState, pos: Any): Long {
        return state.getRenderingSeed(pos as net.minecraft.util.math.BlockPos)
    }

    override fun matrixStackPush(stack: Any): Any {
        return (stack as net.minecraft.client.util.math.MatrixStack).push()
    }

    override fun matrixStackPop(stack: Any) {
        (stack as net.minecraft.client.util.math.MatrixStack).pop()
    }

    override fun blockRenderManagerGetModel(manager: Any, state: BlockState): Any {
        return (manager as net.minecraft.client.render.block.BlockRenderManager).getModel(state)
    }

    @Suppress("UNCHECKED_CAST")
    override fun blockRenderManagerRenderBlock(manager: Any, state: BlockState, pos: Any, world: Any, matrixStack: Any, consumer: Any, checkSides: Boolean, parts: List<Any>): Boolean {
        (manager as net.minecraft.client.render.block.BlockRenderManager).renderBlock(
            state,
            pos as net.minecraft.util.math.BlockPos,
            world as net.minecraft.world.BlockRenderView,
            matrixStack as net.minecraft.client.util.math.MatrixStack,
            consumer as net.minecraft.client.render.VertexConsumer,
            checkSides,
            parts as List<net.minecraft.client.render.model.BlockModelPart>
        )
        return true
    }

    override fun blockRenderManagerRenderFluid(manager: Any, pos: Any, world: Any, consumer: Any, state: BlockState, fluidState: Any): Boolean {
        (manager as net.minecraft.client.render.block.BlockRenderManager).renderFluid(
            pos as net.minecraft.util.math.BlockPos,
            world as net.minecraft.world.BlockRenderView,
            consumer as net.minecraft.client.render.VertexConsumer,
            state,
            fluidState as net.minecraft.fluid.FluidState
        )
        return true
    }

    // Entity API helpers for 1.21.11
    override fun entityIsRemoved(entity: Any): Boolean {
        return (entity as net.minecraft.entity.Entity).isRemoved
    }

    override fun entityGetVehicle(entity: Any): Any? {
        return (entity as net.minecraft.entity.Entity).vehicle
    }

    override fun entityGetUuid(entity: Any): java.util.UUID {
        return (entity as net.minecraft.entity.Entity).uuid
    }

    override fun entityGetX(entity: Any): Double {
        return (entity as net.minecraft.entity.Entity).x
    }

    override fun entityGetY(entity: Any): Double {
        return (entity as net.minecraft.entity.Entity).y
    }

    override fun entityGetZ(entity: Any): Double {
        return (entity as net.minecraft.entity.Entity).z
    }

    override fun entityGetYaw(entity: Any): Float {
        return (entity as net.minecraft.entity.Entity).yaw
    }

    override fun entityGetPitch(entity: Any): Float {
        return (entity as net.minecraft.entity.Entity).pitch
    }

    override fun entityGetPassengerList(entity: Any): List<Any> {
        return (entity as net.minecraft.entity.Entity).passengerList
    }

    override fun entitySetUuid(entity: Any, uuid: java.util.UUID) {
        (entity as net.minecraft.entity.Entity).setUuid(uuid)
    }

    override fun entitySetPositionAndAngles(entity: Any, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        (entity as net.minecraft.entity.Entity).refreshPositionAndAngles(x, y, z, yaw, pitch)
    }

    override fun entityRefreshPositionAndAngles(entity: Any) {
        val e = entity as net.minecraft.entity.Entity
        e.refreshPositionAndAngles(e.x, e.y, e.z, e.yaw, e.pitch)
    }

    override fun entityUpdatePassengerPosition(entity: Any, passenger: Any) {
        (entity as net.minecraft.entity.Entity).updatePassengerPosition(passenger as net.minecraft.entity.Entity)
    }

    override fun entityTypeLoadEntityWithPassengers(tag: NbtCompound, world: Any, spawnReason: Any, entityProcessor: (Any) -> Any): Any? {
        return net.minecraft.entity.EntityType.loadEntityWithPassengers(
            tag,
            world as net.minecraft.server.world.ServerWorld,
            spawnReason as net.minecraft.entity.SpawnReason,
            net.minecraft.entity.LoadedEntityProcessor { entity ->
                entityProcessor(entity) as? net.minecraft.entity.Entity
            },
        )
    }

    override fun worldSpawnNewEntityAndPassengers(world: Any, entity: Any): Boolean {
        return (world as net.minecraft.server.world.ServerWorld).spawnNewEntityAndPassengers(entity as net.minecraft.entity.Entity)
    }

    override fun worldGetOtherEntities(world: Any, entity: Any, box: Any): List<Any> {
        return (world as net.minecraft.world.World).getOtherEntities(
            entity as net.minecraft.entity.Entity,
            box as net.minecraft.util.math.Box
        )
    }

    // MinecraftClient API helpers for 1.21.11
    override fun clientGetServer(client: Any): Any? {
        return (client as MinecraftClient).server
    }

    override fun clientGetWorldRegistryKey(client: Any): Any? {
        return (client as MinecraftClient).world?.registryKey
    }

    override fun serverExecute(server: Any, task: Runnable) {
        (server as net.minecraft.server.integrated.IntegratedServer).execute(task)
    }

    @Suppress("UNCHECKED_CAST")
    override fun serverGetWorld(server: Any, registryKey: Any): Any? {
        return (server as net.minecraft.server.integrated.IntegratedServer).getWorld(registryKey as net.minecraft.registry.RegistryKey<net.minecraft.world.World>)
    }

    override fun playerSendMessage(player: Any, message: Any, overlay: Boolean) {
        (player as net.minecraft.entity.player.PlayerEntity).sendMessage(message as net.minecraft.text.Text, overlay)
    }

    // Direction/BlockState API helpers for 1.21.11
    override fun directionGetVector(direction: Any): Any {
        return (direction as net.minecraft.util.math.Direction).vector
    }

    override fun blockStateStringify(state: BlockState): String {
        return BlockArgumentParser.stringifyBlockState(state)
    }

    fun rawBlockStateId(state: BlockState): Int {
        return Block.getRawIdFromState(state)
    }

    // Registry/BlockArgumentParser API helpers for 1.21.11
    override fun worldGetRegistryManager(world: Any): Any {
        return (world as net.minecraft.world.World).registryManager
    }

    override fun blockArgumentParserBlock(registry: Any, state: String): Any {
        return net.minecraft.command.argument.BlockArgumentParser.block(
            (registry as DynamicRegistryManager).getOrThrow(RegistryKeys.BLOCK),
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
        val renderObjects = ArrayList<RenderPass.RenderObject<Unit>>(drawList.size)
        for (i in drawList.indices) {
            val entry = drawList[i]
            val vb = entry.buffer.vertexBufferGpu ?: return false
            val ib = entry.buffer.indexBufferGpu ?: return false
            val slice = uniformSlices[i]
            renderObjects += RenderPass.RenderObject<Unit>(
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
    private var loggedAtlasResult: Boolean = false
    private var loggedBindFailure: Boolean = false

    fun getBlockAtlasTextureView(client: MinecraftClient): GpuTextureView? {
        return try {
            val atlas = client.atlasManager?.getAtlasTexture(net.minecraft.util.Identifier.of("minecraft", "blocks"))
            currentAtlasSampler = atlas?.javaClass?.methods
                ?.firstOrNull { it.name == "getSampler" && it.parameterCount == 0 }
                ?.invoke(atlas)
            val view = atlas?.getGlTextureView()
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

    fun bindTextureToRenderPass(pass: RenderPass, samplerName: String, textureView: GpuTextureView) {
        val sampler = currentAtlasSampler
        val bindTexture = renderPassBindTexture
        if (bindTexture != null && sampler != null) {
            try {
                bindTexture.invoke(pass, samplerName, textureView, sampler)
            } catch (e: Exception) {
                logger.warn("[Axion GPU] bindTexture.invoke failed for {}", samplerName, e)
            }
            return
        }

        val bindSampler = renderPassBindSampler
        if (bindSampler != null) {
            try {
                bindSampler.invoke(pass, samplerName, textureView)
            } catch (e: Exception) {
                logger.warn("[Axion GPU] bindSampler.invoke failed for {}", samplerName, e)
            }
        } else if (!loggedBindFailure) {
            loggedBindFailure = true
            logger.error("[Axion GPU] bindTextureToRenderPass: no bind method available — GPU preview will be invisible")
        }
    }

    fun getRenderPipeline(layer: RenderLayer): RenderPipeline? {
        return try {
            layer.renderPipeline
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

    fun getPreviewShellPipeline(vertexFormat: VertexFormat, drawMode: VertexFormat.DrawMode): RenderPipeline? {
        return try {
            previewShellPipelines.computeIfAbsent(drawMode) {
                RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("axion", "preview_shell"))
                    .withVertexShader(Identifier.of("axion", "core/preview_shell"))
                    .withFragmentShader(Identifier.of("axion", "core/preview_shell"))
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withCull(true)
                    .withVertexFormat(vertexFormat, drawMode)
                    .build()
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
        dynamicUniforms: net.minecraft.client.gl.DynamicUniforms,
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
        val output = NbtWriteView.create(ErrorReporter.EMPTY, entity.getEntityWorld().registryManager)
        return if (entity.saveSelfData(output)) output.nbt else null
    }

    fun drawGuiTexture(
        context: DrawContext,
        texture: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height)
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
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u.toFloat(), v.toFloat(), width, height, textureWidth, textureHeight)
    }

    private val cameraPosField: Field? by lazy {
        try {
            Camera::class.java.getDeclaredField("pos").apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            Camera::class.java.declaredFields.firstOrNull { it.type == Vec3d::class.java }
                ?.apply { isAccessible = true }
        }
    }

    private val cameraPosMethod: java.lang.reflect.Method? by lazy {
        Camera::class.java.methods.firstOrNull { it.name == "getPos" && it.parameterCount == 0 }
            ?: Camera::class.java.methods.firstOrNull {
                it.parameterCount == 0 && it.returnType == Vec3d::class.java
            }
    }

    fun getCameraPos(camera: Camera): Vec3d {
        cameraPosMethod?.let { m ->
            try { return m.invoke(camera) as Vec3d } catch (_: Exception) {}
        }

        cameraPosField?.let { f ->
            try { return f.get(camera) as Vec3d } catch (_: Exception) {}
        }

        throw IllegalStateException("Cannot access camera position — no method or field found on Camera class")
    }

    // ItemStack codec helpers for hotbar save/load (1.21.11 uses reflection directly)
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
        // 1.21.11 PacketCodec API - try ofStatic with 2 parameters, fallback to any 2-parameter static method
        val codecClass = PacketCodec::class.java

        // Try ofStatic first
        val method = codecClass.methods.firstOrNull { it.name == "ofStatic" && it.parameterCount == 2 }
            // Fallback: try any static method with 2 parameters that returns PacketCodec
            ?: codecClass.methods.firstOrNull {
                it.parameterCount == 2 &&
                it.returnType == codecClass &&
                java.lang.reflect.Modifier.isStatic(it.modifiers)
            }
            ?: throw NoSuchMethodError("No compatible PacketCodec factory method found in 1.21.11")

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

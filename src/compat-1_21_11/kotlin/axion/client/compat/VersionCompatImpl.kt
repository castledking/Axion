package axion.client.compat

import axion.client.render.AxionWorldRenderContext
import axion.client.render.gpu.ChunkedPreviewLifecycle
import axion.client.render.gpu.SectionDrawEntry
import axion.client.network.AxionPluginPayload
import axion.common.compat.VersionCompat
import java.lang.reflect.Field
import axion.common.model.ClipboardBuffer
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.Camera
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.command.argument.BlockArgumentParser
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

/**
 * Version compatibility implementation for Minecraft 1.21.11
 */
object VersionCompatImpl : VersionCompat {
    private var currentAtlasSampler: net.minecraft.client.gl.GpuSampler? = null
    private val previewShellPipelines = java.util.EnumMap<VertexFormat.DrawMode, RenderPipeline>(VertexFormat.DrawMode::class.java)

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

    fun createLiteral(text: String): Text = Text.literal(text)

    fun formatText(text: MutableText, formatting: Formatting): MutableText = text.formatted(formatting)

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
        return session.render(context, color, alpha).handled
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

    override fun blockRenderManagerRenderBlock(manager: Any, state: BlockState, pos: Any, world: Any, matrixStack: Any, consumer: Any, checkSides: Boolean, parts: List<Any>): Boolean {
        return (manager as net.minecraft.client.render.block.BlockRenderManager).renderBlock(
            state,
            pos as net.minecraft.util.math.BlockPos,
            world as net.minecraft.world.BlockRenderView,
            matrixStack as net.minecraft.client.util.math.MatrixStack,
            consumer as net.minecraft.client.render.VertexConsumer,
            checkSides,
            parts as List<net.minecraft.client.render.model.BlockModelPart>
        )
    }

    override fun blockRenderManagerRenderFluid(manager: Any, pos: Any, world: Any, consumer: Any, state: BlockState, fluidState: Any): Boolean {
        return (manager as net.minecraft.client.render.block.BlockRenderManager).renderFluid(
            pos as net.minecraft.util.math.BlockPos,
            world as net.minecraft.world.BlockRenderView,
            consumer as net.minecraft.client.render.VertexConsumer,
            state,
            fluidState as net.minecraft.fluid.FluidState
        )
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

    override fun entityRefreshPositionAndAngles(entity: Any) {
        (entity as net.minecraft.entity.Entity).refreshPositionAndAngles()
    }

    override fun entityUpdatePassengerPosition(entity: Any, passenger: Any) {
        (entity as net.minecraft.entity.Entity).updatePassengerPosition(passenger as net.minecraft.entity.Entity)
    }

    override fun entityTypeLoadEntityWithPassengers(tag: NbtCompound, world: Any, spawnReason: Any, entityProcessor: (Any) -> Any): Any? {
        return net.minecraft.entity.EntityType.loadEntityWithPassengers(
            tag,
            world as net.minecraft.server.world.ServerWorld,
            spawnReason as net.minecraft.entity.SpawnReason,
            entityProcessor
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
        return state.toString()
    }

    // Registry/BlockArgumentParser API helpers for 1.21.11
    override fun worldGetRegistryManager(world: Any): Any {
        return (world as net.minecraft.world.World).registryManager
    }

    override fun blockArgumentParserBlock(registry: Any, state: String): Any {
        return net.minecraft.command.argument.BlockArgumentParser.block(
            registry as net.minecraft.registry.RegistryManager,
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

    fun getRenderPipeline(layer: RenderLayer): RenderPipeline {
        return layer.renderPipeline
    }

    fun getPreviewShellPipeline(vertexFormat: VertexFormat, drawMode: VertexFormat.DrawMode): RenderPipeline {
        return previewShellPipelines.computeIfAbsent(drawMode) {
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
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height)
    }

    private val cameraPosField: Field? by lazy {
        try {
            Camera::class.java.getDeclaredField("pos").apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            null
        }
    }

    fun getCameraPos(camera: Camera): Vec3d {
        val field = cameraPosField
        if (field != null) {
            try {
                return field.get(camera) as Vec3d
            } catch (_: Exception) {
                // Fall through
            }
        }
        // Last resort: try the position() method (might exist in some versions)
        try {
            return camera.position()
        } catch (_: NoSuchMethodException) {
            throw IllegalStateException("Cannot access camera position")
        }
    }

    // ItemStack codec helpers for hotbar save/load (1.21.11 uses reflection directly)
    override fun itemStackEncode(registryManager: Any, stack: Any): ByteArray? {
        return null // Not used in 1.21.11, SavedHotbarController uses its own reflection
    }

    override fun itemStackDecode(registryManager: Any, bytes: ByteArray): Any? {
        return null // Not used in 1.21.11, SavedHotbarController uses its own reflection
    }
}

package axion.client.render.gpu

import axion.client.compat.CameraAccess
import axion.client.compat.VersionCompatImpl
import axion.client.render.AxionPreviewBuffer
import axion.client.render.AxionWorldRenderContext
import axion.client.render.RenderLayerCompat
import axion.client.render.ShaderPackCompat
import axion.client.render.TintedAlphaVertexConsumer
import axion.client.render.getBuffer
import axion.client.render.defaultState
import axion.client.render.getRenderingSeedCompat
import axion.common.model.ClipboardBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.BuiltBuffer
import net.minecraft.client.render.BlockRenderManager
import net.minecraft.client.util.BufferAllocator
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import axion.client.compat.blockPosFromLong
import net.minecraft.world.BlockRenderView
import net.minecraft.world.LightType
import net.minecraft.world.biome.ColorResolver
import net.minecraft.world.chunk.light.LightingProvider
import net.minecraft.world.level.CardinalLighting
import com.mojang.blaze3d.vertex.QuadInstance
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.renderer.block.BlockQuadOutput
import net.minecraft.client.resources.model.geometry.BakedQuad
import org.joml.Matrix4f
import org.joml.Quaternionf

class ChunkedPreviewSession(val previewId: String) : AutoCloseable {
    private val store = ChunkedBooleanStore()
    private val states = ChunkedStateMap()
    private val chunkBuffers = Long2ObjectOpenHashMap<AxionPreviewBuffer>()
    private var lastSignature: Long = 0
    private var anchoredClipboard: ClipboardBuffer? = null
    private var anchoredSurfaceClipboard: ClipboardBuffer? = null
    private var anchoredSingleOrigin: BlockPos? = null
    private var translationDelta: Vec3i = Vec3i.ZERO

    val isEmpty: Boolean get() = store.isEmpty()
    val totalCells: Int get() = store.size
    fun totalSurfaceCells(): Int = store.size

    fun setFromClipboard(
        clipboard: ClipboardBuffer,
        surfaceClipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
    ): Boolean {
        val signature = computeSignature(clipboard, surfaceClipboard, origins)
        if (signature == lastSignature && lastSignature != 0L) return false

        val nextSingleOrigin = if (origins.size == 1) origins.first() else null
        if (
            clipboard === anchoredClipboard &&
            surfaceClipboard === anchoredSurfaceClipboard &&
            nextSingleOrigin != null &&
            anchoredSingleOrigin != null &&
            !chunkBuffers.isEmpty()
        ) {
            val anchor = anchoredSingleOrigin ?: nextSingleOrigin
            translationDelta = Vec3i(
                nextSingleOrigin.x - anchor.x,
                nextSingleOrigin.y - anchor.y,
                nextSingleOrigin.z - anchor.z,
            )
            lastSignature = signature
            return true
        }

        lastSignature = signature
        rebuildFull(clipboard, surfaceClipboard, origins)
        anchoredClipboard = clipboard
        anchoredSurfaceClipboard = surfaceClipboard
        anchoredSingleOrigin = nextSingleOrigin
        translationDelta = Vec3i.ZERO
        return true
    }

    fun clear() {
        store.clear()
        states.clear()
        chunkBuffers.values.forEach { it.close() }
        chunkBuffers.clear()
        lastSignature = 0
        anchoredClipboard = null
        anchoredSurfaceClipboard = null
        anchoredSingleOrigin = null
        translationDelta = Vec3i.ZERO
    }

    fun render(context: AxionWorldRenderContext, color: Int, alpha: Int): ChunkedDrawResult {
        if (store.isEmpty()) return ChunkedDrawResult.NO_BUFFERS
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return ChunkedDrawResult.FAILED
        val camera = client.gameRenderer.camera
        val cameraPos = CameraAccess.getPos(camera)
        val effectiveCamera = PreviewTranslucencySortPolicy.effectiveCamera(
            cameraPos.x,
            cameraPos.y,
            cameraPos.z,
            translationDelta.x,
            translationDelta.y,
            translationDelta.z,
        )

        refreshDirtyBuffers(world, effectiveCamera)
        if (chunkBuffers.isEmpty()) return ChunkedDrawResult.NO_BUFFERS

        if (ShaderPackCompat.shouldDisableDirectGpuPreview()) {
            renderLegacy(context, world, color, alpha, translationDelta)
            return ChunkedDrawResult.DREW
        }

        resortBuffers(effectiveCamera)

        // The END_MAIN pose stack is not a usable view matrix for a direct GPU
        // pass. Build the view rotation the way vanilla does: the conjugate of
        // the camera quaternion, with translation supplied per section as
        // (origin + delta - cameraPos).
        val baseModelView = Matrix4f().rotation(camera.rotation().conjugate(Quaternionf()))
        val projection = RenderSystem.getProjectionMatrixBuffer()
            ?: return ChunkedDrawResult.FAILED
        val sceneDepth = client.framebuffer.depthTextureView
        if (sceneDepth == null) {
            renderLegacy(context, world, color, alpha, translationDelta)
            return ChunkedDrawResult.DREW
        }
        val result = drawPostWorld(
            color, alpha, translationDelta,
            baseModelView, cameraPos, projection, sceneDepth,
        )
        if (result != ChunkedDrawResult.DREW) {
            renderLegacy(context, world, color, alpha, translationDelta)
        }
        return ChunkedDrawResult.DREW
    }

    private fun renderLegacy(
        context: AxionWorldRenderContext,
        world: net.minecraft.client.world.ClientWorld,
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera
        val cameraPos = CameraAccess.getPos(camera)
        val blockRenderer = BlockRenderManager(true, true, client.blockColors)
        val modelSet = client.modelManager.blockStateModelSet
        val consumer = TintedAlphaVertexConsumer(
            context.consumers().getBuffer(
                VersionCompatImpl.getBufferedPreviewShellLayer(RenderLayerCompat.blockTranslucentCull()),
            ),
            alpha / 255.0f,
            color,
        )
        val statesView = states.asMap()
        val cameraX = cameraPos.x - translationDelta.x
        val cameraY = cameraPos.y - translationDelta.y
        val cameraZ = cameraPos.z - translationDelta.z

        store.forEachSection { sectionKey, _ ->
            val surface = ChunkMeshTessellator.buildSectionSurface(store, sectionKey, statesView)
            for (packed in surface) {
                val state = statesView[packed] ?: continue
                if (state.isAir || state.renderShape != BlockRenderType.MODEL) continue
                val pos = blockPosFromLong(packed)
                val model = modelSet.get(state)
                val previewView = PreviewBlockRenderView(world, statesView, pos)
                val output = BlockQuadOutput { x: Float, y: Float, z: Float, quad: BakedQuad, quadInstance: QuadInstance ->
                    consumer.putBlockBakedQuad(
                        x - cameraX.toFloat(),
                        y - cameraY.toFloat(),
                        z - cameraZ.toFloat(),
                        quad,
                        quadInstance,
                    )
                }
                blockRenderer.tesselateBlock(
                    output,
                    pos.x.toFloat(),
                    pos.y.toFloat(),
                    pos.z.toFloat(),
                    previewView,
                    pos,
                    state,
                    model,
                    state.getRenderingSeedCompat(pos),
                )
            }
        }
    }

    fun drawPostWorld(
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
        baseModelView: Matrix4f,
        cameraPos: Vec3d?,
        projection: GpuBufferSlice,
        sceneDepth: com.mojang.blaze3d.textures.GpuTextureView,
    ): ChunkedDrawResult {
        if (chunkBuffers.isEmpty()) return ChunkedDrawResult.NO_BUFFERS
        return AxionPreviewBlockDrawer.drawChunked(
            chunkBuffers, color, alpha, translationDelta,
            baseModelView, cameraPos, projection, sceneDepth,
        )
    }

    override fun close() {
        clear()
    }

    private fun rebuildFull(
        clipboard: ClipboardBuffer,
        surfaceClipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
    ) {
        store.clear()
        states.clear()
        val occupiedCells = clipboard.nonAirCells()
        val surfaceCells = surfaceClipboard.nonAirCells()
        val stateHalo = PreviewStateHalo.retain(occupiedCells, surfaceCells)
        origins.forEach { origin ->
            stateHalo.forEach { cell ->
                val pos = cell.absolutePos(origin)
                states.put(pos, cell.state)
            }
            surfaceCells.forEach { cell ->
                val pos = cell.absolutePos(origin)
                store.add(pos)
                states.put(pos, cell.state)
            }
        }
    }

    private fun refreshDirtyBuffers(
        world: net.minecraft.client.world.ClientWorld,
        effectiveCamera: PreviewTranslucencySortPolicy.Point,
    ) {
        val dirty = store.consumeDirty()
        if (dirty.isEmpty()) return

        val sectionOrigins = ArrayList<PreviewTranslucencySortPolicy.SectionOrigin>()
        store.forEachSection { sectionKey, _ ->
            sectionOrigins += PreviewTranslucencySortPolicy.SectionOrigin(
                ChunkedBooleanStore.sectionX(sectionKey) shl 4,
                ChunkedBooleanStore.sectionY(sectionKey) shl 4,
                ChunkedBooleanStore.sectionZ(sectionKey) shl 4,
            )
        }
        val meshPlan = PreviewTranslucencySortPolicy.globalMeshPlan(sectionOrigins)
        if (meshPlan == null) {
            chunkBuffers.values.forEach { it.close() }
            chunkBuffers.clear()
            return
        }

        val statesView = states.asMap()
        val builtSection = try {
            buildGlobalBuffer(meshPlan.anchor, world, statesView)
        } catch (t: Throwable) {
            store.markAllDirty()
            throw t
        }
        if (builtSection == null) {
            chunkBuffers.values.forEach { it.close() }
            chunkBuffers.clear()
            return
        }

        val builtBuffer = builtSection.mesh
        val sortX = effectiveCamera.x - meshPlan.anchor.x
        val sortY = effectiveCamera.y - meshPlan.anchor.y
        val sortZ = effectiveCamera.z - meshPlan.anchor.z
        val replacement = AxionPreviewBuffer()
        try {
            val sortState = builtBuffer.sortQuads(
                builtSection.allocator,
                VertexSorting.byDistance(sortX, sortY, sortZ),
            )
            replacement.upload(
                builtBuffer,
                sortState,
                sortX,
                sortY,
                sortZ,
            )
        } catch (t: Throwable) {
            replacement.close()
            store.markAllDirty()
            throw t
        } finally {
            builtBuffer.close()
            builtSection.allocator.close()
        }

        chunkBuffers.values.forEach { it.close() }
        chunkBuffers.clear()
        val anchorKey = ChunkedBooleanStore.sectionKey(
            meshPlan.anchor.x,
            meshPlan.anchor.y,
            meshPlan.anchor.z,
        )
        chunkBuffers.put(anchorKey, replacement)
        check(chunkBuffers.size == meshPlan.batchCount) {
            "Translucent preview must use one globally sorted GPU buffer"
        }
    }

    private fun resortBuffers(effectiveCamera: PreviewTranslucencySortPolicy.Point) {
        val iter = chunkBuffers.long2ObjectEntrySet().fastIterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val sectionKey = entry.longKey
            entry.value.resort(
                effectiveCamera.x - (ChunkedBooleanStore.sectionX(sectionKey) shl 4),
                effectiveCamera.y - (ChunkedBooleanStore.sectionY(sectionKey) shl 4),
                effectiveCamera.z - (ChunkedBooleanStore.sectionZ(sectionKey) shl 4),
            )
        }
    }

    private data class PendingSectionMesh(
        val mesh: BuiltBuffer,
        val allocator: BufferAllocator,
    )

    private fun buildGlobalBuffer(
        anchor: PreviewTranslucencySortPolicy.SectionOrigin,
        world: net.minecraft.client.world.ClientWorld,
        statesByPosition: Map<Long, BlockState>,
    ): PendingSectionMesh? {
        val client = MinecraftClient.getInstance()
        val blockRenderer = BlockRenderManager(true, true, client.blockColors)
        val modelSet = client.modelManager.blockStateModelSet
        val layer = RenderLayerCompat.blockTranslucentCull()
        val allocator = BufferAllocator(layer.bufferSize())
        return try {
            val bufferBuilder = BufferBuilder(allocator, layer.mode(), layer.format())

            var rendered = false
            store.forEachSection { sectionKey, _ ->
                val surface = ChunkMeshTessellator.buildSectionSurface(store, sectionKey, statesByPosition)
                for (packed in surface) {
                    val state = statesByPosition[packed] ?: continue
                    if (state.isAir || state.renderShape != BlockRenderType.MODEL) continue
                    val pos = blockPosFromLong(packed)
                    val model = modelSet.get(state)
                    val previewView = PreviewBlockRenderView(world, statesByPosition, pos)
                    val output = BlockQuadOutput { x: Float, y: Float, z: Float, quad: BakedQuad, quadInstance: QuadInstance ->
                        bufferBuilder.putBlockBakedQuad(x, y, z, quad, quadInstance)
                    }
                    blockRenderer.tesselateBlock(
                        output,
                        (pos.x - anchor.x).toFloat(),
                        (pos.y - anchor.y).toFloat(),
                        (pos.z - anchor.z).toFloat(),
                        previewView,
                        pos,
                        state,
                        model,
                        state.getRenderingSeedCompat(pos),
                    )
                    rendered = true
                }
            }

            val mesh = if (rendered) bufferBuilder.build() else null
            if (mesh == null) {
                allocator.close()
                null
            } else {
                PendingSectionMesh(mesh, allocator)
            }
        } catch (t: Throwable) {
            allocator.close()
            throw t
        }
    }

    private fun computeSignature(
        clipboard: ClipboardBuffer,
        surfaceClipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
    ): Long {
        var sig = System.identityHashCode(clipboard).toLong()
        sig = sig * 31L + System.identityHashCode(surfaceClipboard)
        origins.forEach { sig = sig * 31L + it.asLong() }
        return if (sig == 0L) 1L else sig
    }

    private inline fun iterateAllCellsInSection(
        store: ChunkedBooleanStore,
        sectionKey: Long,
        action: (packed: Long) -> Unit,
    ) {
        val section = store.rawSection(sectionKey) ?: return
        val baseX = ChunkedBooleanStore.sectionX(sectionKey) shl 4
        val baseY = ChunkedBooleanStore.sectionY(sectionKey) shl 4
        val baseZ = ChunkedBooleanStore.sectionZ(sectionKey) shl 4
        for (z in 0..15) {
            for (y in 0..15) {
                val v = section[y + z * 16].toInt()
                if (v == 0) continue
                for (x in 0..15) {
                    if ((v and (1 shl x)) != 0) {
                        action(BlockPos.asLong(baseX + x, baseY + y, baseZ + z))
                    }
                }
            }
        }
    }

    private class PreviewBlockRenderView(
        private val world: net.minecraft.client.world.ClientWorld,
        private val statesByPosition: Map<Long, BlockState>,
        private val renderingPos: BlockPos? = null,
    ) : BlockRenderView {
        private val airState: BlockState = Blocks.AIR.defaultState

        override fun getBlockEntity(pos: net.minecraft.core.BlockPos) =
            null

        override fun getBlockState(pos: net.minecraft.core.BlockPos): BlockState {
            if (renderingPos != null && PreviewOcclusionPolicy.isDirectNeighbor(
                    pos.x, pos.y, pos.z,
                    renderingPos.x, renderingPos.y, renderingPos.z,
                )
            ) {
                val renderingState = statesByPosition[renderingPos.asLong()]
                val neighborState = statesByPosition[pos.asLong()]
                if (PreviewOcclusionPolicy.shouldReplaceNeighborWithAir(
                        rendering = renderingState,
                        neighbor = neighborState,
                        isSameOcclusionGroup = { first, second -> first.block == second.block },
                        isOpaqueFullCube = PreviewOcclusionCompat::isOpaqueFullCube,
                    )
                ) {
                    return airState
                }
            }
            return statesByPosition[pos.asLong()] ?: airState
        }

        override fun getFluidState(pos: net.minecraft.core.BlockPos) = getBlockState(pos).fluidState

        override fun getHeight(): Int = world.height

        override fun getMinY(): Int = world.minY

        override fun getLightEngine(): LightingProvider = world.lightEngine

        override fun getBrightness(layer: LightType, pos: net.minecraft.core.BlockPos): Int =
            15

        override fun cardinalLighting(): CardinalLighting = world.cardinalLighting()

        override fun getBlockTint(pos: net.minecraft.core.BlockPos, colorResolver: ColorResolver): Int =
            world.getBlockTint(pos, colorResolver)
    }
}

package axion.client.render.gpu

import axion.client.compat.CameraAccess
import axion.client.render.AxionPreviewBuffer
import axion.client.render.AxionWorldRenderContext
import axion.client.render.RenderLayerCompat
import axion.client.render.defaultState
import axion.client.render.getRenderingSeedCompat
import axion.client.render.isOpaqueFullCube
import axion.common.model.ClipboardBuffer
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
import net.minecraft.client.renderer.block.BlockQuadOutput
import net.minecraft.client.resources.model.geometry.BakedQuad
import org.joml.Matrix4f

class ChunkedPreviewSession(val previewId: String) : AutoCloseable {
    private val store = ChunkedBooleanStore()
    private val states = ChunkedStateMap()
    private val chunkBuffers = Long2ObjectOpenHashMap<AxionPreviewBuffer>()
    private var lastSignature: Long = 0
    private var anchoredClipboard: ClipboardBuffer? = null
    private var anchoredSingleOrigin: BlockPos? = null
    private var translationDelta: Vec3i = Vec3i.ZERO

    val isEmpty: Boolean get() = store.isEmpty()
    val totalCells: Int get() = store.size
    fun totalSurfaceCells(): Int = store.size

    fun setFromClipboard(clipboard: ClipboardBuffer, origins: Collection<BlockPos>): Boolean {
        val signature = computeSignature(clipboard, origins)
        if (signature == lastSignature && lastSignature != 0L) return false

        val nextSingleOrigin = if (origins.size == 1) origins.first() else null
        if (
            clipboard === anchoredClipboard &&
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
        rebuildFull(clipboard, origins)
        anchoredClipboard = clipboard
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
    }

    fun render(context: AxionWorldRenderContext, color: Int, alpha: Int): ChunkedDrawResult {
        if (store.isEmpty()) return ChunkedDrawResult.NO_BUFFERS
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return ChunkedDrawResult.FAILED

        refreshDirtyBuffers(world)
        if (chunkBuffers.isEmpty()) return ChunkedDrawResult.NO_BUFFERS

        val camera = client.gameRenderer.camera ?: return ChunkedDrawResult.FAILED
        val cameraPos = CameraAccess.getPos(camera)
        val baseModelView = Matrix4f(context.matrices().peek().pose())
        return drawDeferred(color, alpha, translationDelta, baseModelView, cameraPos)
    }

    fun drawDeferred(
        color: Int,
        alpha: Int,
        translationDelta: Vec3i,
        baseModelView: Matrix4f,
        cameraPos: Vec3d? = null,
        cullingModelView: org.joml.Matrix4fc? = null,
        projectionMatrix: org.joml.Matrix4fc? = null,
    ): ChunkedDrawResult {
        if (chunkBuffers.isEmpty()) return ChunkedDrawResult.NO_BUFFERS
        return AxionPreviewBlockDrawer.drawChunked(
            chunkBuffers, color, alpha, translationDelta,
            baseModelView, cameraPos, cullingModelView, projectionMatrix,
        )
    }

    override fun close() {
        clear()
    }

    private fun rebuildFull(clipboard: ClipboardBuffer, origins: Collection<BlockPos>) {
        val previousPositions = HashSet<Long>(store.size)
        store.forEachSection { sectionKey, _ ->
            iterateAllCellsInSection(store, sectionKey) { packed -> previousPositions += packed }
        }

        val newPositions = HashSet<Long>()
        val occupiedCells = clipboard.nonAirCells()
        origins.forEach { origin ->
            occupiedCells.forEach { cell ->
                val pos = cell.absolutePos(origin)
                val packed = pos.asLong()
                newPositions += packed
                states.put(pos, cell.state)
            }
        }

        previousPositions.forEach { packed ->
            if (packed !in newPositions) {
                store.remove(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed))
                states.remove(packed)
            }
        }
        newPositions.forEach { packed ->
            if (packed !in previousPositions) {
                store.add(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed))
            }
        }
    }

    private fun refreshDirtyBuffers(world: net.minecraft.client.world.ClientWorld) {
        val dirty = store.consumeDirty()
        if (dirty.isEmpty()) return

        val statesView = states.asMap()
        val iter = dirty.iterator()
        while (iter.hasNext()) {
            val sectionKey = iter.nextLong()
            val rawSection = store.rawSection(sectionKey)
            if (rawSection == null) {
                chunkBuffers.remove(sectionKey)?.close()
                continue
            }

            val builtBuffer = buildSectionBuffer(sectionKey, world, statesView)
            if (builtBuffer == null) {
                chunkBuffers.remove(sectionKey)?.close()
            } else {
                chunkBuffers.computeIfAbsent(sectionKey) { AxionPreviewBuffer() }.upload(builtBuffer)
                builtBuffer.close()
            }
        }
    }

    private fun buildSectionBuffer(
        sectionKey: Long,
        world: net.minecraft.client.world.ClientWorld,
        statesByPosition: Map<Long, BlockState>,
    ): BuiltBuffer? {
        val surface = ChunkMeshTessellator.buildSectionSurface(store, sectionKey, statesByPosition)
        if (surface.isEmpty()) return null

        val client = MinecraftClient.getInstance()
        val blockRenderer = BlockRenderManager(true, true, client.blockColors)
        val modelSet = client.modelManager.blockStateModelSet
        val sectionOriginX = ChunkedBooleanStore.sectionX(sectionKey) shl 4
        val sectionOriginY = ChunkedBooleanStore.sectionY(sectionKey) shl 4
        val sectionOriginZ = ChunkedBooleanStore.sectionZ(sectionKey) shl 4
        val layer = RenderLayerCompat.blockTranslucentCull()
        val allocator = BufferAllocator(layer.bufferSize())
        val bufferBuilder = BufferBuilder(allocator, layer.mode(), layer.format())

        var rendered = false
        for (packed in surface) {
            val state = statesByPosition[packed] ?: continue
            if (state.isAir || state.renderShape != BlockRenderType.MODEL) continue
            val pos = blockPosFromLong(packed)
            val model = modelSet.get(state)
            val previewView = PreviewBlockRenderView(world, statesByPosition, pos)
            val output = BlockQuadOutput { x: Float, y: Float, z: Float, quad: BakedQuad, quadInstance: QuadInstance ->
                bufferBuilder.putBlockBakedQuad(
                    x - sectionOriginX.toFloat(),
                    y - sectionOriginY.toFloat(),
                    z - sectionOriginZ.toFloat(),
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
            rendered = true
        }

        return if (rendered) bufferBuilder.build() else null
    }

    private fun computeSignature(clipboard: ClipboardBuffer, origins: Collection<BlockPos>): Long {
        var sig = System.identityHashCode(clipboard).toLong()
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
            if (renderingPos != null && pos == renderingPos.above()) {
                val aboveState = statesByPosition[pos.asLong()]
                if (aboveState != null && !aboveState.isOpaqueFullCube) {
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

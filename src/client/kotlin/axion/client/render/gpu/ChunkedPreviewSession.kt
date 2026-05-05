package axion.client.render.gpu

import axion.client.render.AxionBlockTessellator
import axion.client.render.AxionPreviewBuffer
import axion.client.render.AxionWorldRenderContext
import axion.client.render.PreviewBlockInfo
import axion.client.render.RenderLayerCompat
import axion.client.render.TintedAlphaVertexConsumer
import axion.common.model.ClipboardBuffer
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.BuiltBuffer
import net.minecraft.client.util.BufferAllocator
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

/**
 * High-level GPU-cached preview rendering session for one tool/preview slot.
 *
 *   1. Tools call [setFromClipboard] when their preview state changes.
 *      Diffing happens at the section level — only chunks whose contents
 *      actually changed get marked dirty.
 *   2. On the first [render] after a dirty change, the affected sections
 *      are tessellated **once** into a per-section [AxionPreviewBuffer]
 *      (a persistent GPU-resident vertex/index buffer pair).
 *   3. Subsequent frames just bind+draw the cached buffers via
 *      [AxionPreviewBlockDrawer]. No tessellation, no upload, no per-cell
 *      VertexConsumer emission.
 *
 * Per-frame cost goes from O(visibleSurfaceCells × blockModelLookup) to
 * O(cachedSections × drawIndexed). For massive previews (church-scale and
 * up) this is the difference between 5 fps and 60+ fps.
 *
 * Falls back gracefully: if [AxionPreviewBlockDrawer] self-disables (e.g.
 * because of an MC-version uniform-name change), the session keeps the
 * cached state metadata around and routes the next frame through the
 * legacy [TintedAlphaVertexConsumer] path so previews still render.
 */
class ChunkedPreviewSession(val previewId: String) : AutoCloseable {
    private val store = ChunkedBooleanStore()
    private val states = ChunkedStateMap()

    /** GPU-resident vertex/index buffers, one per occupied 16³ section. */
    private val chunkBuffers = Long2ObjectOpenHashMap<AxionPreviewBuffer>()

    /** Last clipboard / origin signature used. Lets us short-circuit no-op updates. */
    private var lastSignature: Long = 0

    // ----- Pure-translation fast path (the scroll-smoothness win) -----
    //
    // When the caller updates with the same clipboard at a translated single
    // origin (typical Move/Clone scroll), we skip the entire diff + retessellate
    // and instead track a delta that the drawer adds to the model-view matrix.
    // The cached chunkBuffers stay valid; rendering at the new origin is just
    // a matrix nudge.
    //
    // When the clipboard identity changes (rotation/mirror/new selection) or
    // the multi-origin layout changes, we fall back to the full rebuild path
    // and re-anchor.

    /** The clipboard whose contents are currently baked into the cached buffers. */
    private var anchoredClipboard: ClipboardBuffer? = null

    /** The single origin the cached buffers were tessellated for, if applicable. */
    private var anchoredSingleOrigin: BlockPos? = null

    /** Cumulative translation since the buffers were last anchored. */
    private var translationDelta: Vec3i = Vec3i.ZERO

    val isEmpty: Boolean get() = store.isEmpty()

    val totalCells: Int get() = store.size

    fun totalSurfaceCells(): Int {
        // Approximate — the actual cached buffer cell count isn't tracked
        // separately. Most callers use this for diagnostic output only.
        return store.size
    }

    /**
     * Replace the session's contents with the cells produced by [clipboard]
     * placed at each of [origins]. Computes a section-level diff against the
     * previous contents so only the changed sections get marked dirty (and
     * thus re-tessellated on the next [render]).
     *
     * Cheap when the new content equals the old (early-outs by signature).
     * Returns true if anything changed.
     */
    fun setFromClipboard(clipboard: ClipboardBuffer, origins: Collection<BlockPos>): Boolean {
        val signature = computeSignature(clipboard, origins)
        if (signature == lastSignature && lastSignature != 0L) {
            return false
        }

        // Always do a full rebuild on signature change. We previously had a
        // "translation delta" fast path that left buffers anchored at the
        // original origin and shifted them via a uniform — but that path was
        // fragile in practice:
        //
        //   * The legacy CPU fallback (used when the GPU drawer self-disables,
        //     or before the first GPU upload completes) tessellates straight
        //     from `store` and has no way to apply the delta.
        //   * The drawer's per-section uniform translation can silently no-op
        //     on certain MC builds, producing a preview that scrolls one block
        //     and then sticks while only the outline (recomputed from the
        //     current origin every frame) keeps moving.
        //   * After many scrolls the delta accumulates, and any frustum / cull
        //     decision keyed off the buffer's anchored section coords starts
        //     disagreeing with where the preview is actually drawn.
        //
        // Section-level dirty tracking keeps the rebuild cheap: only sections
        // whose contents actually changed get re-tessellated. In the common
        // "scroll one block in a single direction" case that's a thin slab of
        // sections at the leading + trailing edge of the bounding volume.
        lastSignature = signature
        rebuildFull(clipboard, origins)
        anchoredClipboard = clipboard
        anchoredSingleOrigin = if (origins.size == 1) origins.first() else null
        translationDelta = Vec3i.ZERO
        return true
    }

    private fun rebuildFull(clipboard: ClipboardBuffer, origins: Collection<BlockPos>) {
        // Snapshot the old position set so we can compute the add/remove diff.
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

        // Apply the diff. The store auto-marks affected sections (and their
        // 6 neighbors) dirty, which is what drives the next render's
        // selective re-tessellation pass.
        previousPositions.forEach { packed ->
            if (packed !in newPositions) {
                store.remove(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed))
                states.remove(packed)
            }
        }
        newPositions.forEach { packed ->
            if (packed !in previousPositions) {
                store.add(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed))
            }
        }
    }

    fun clear() {
        store.clear()
        states.clear()
        // Free GPU resources for any cached sections — they'll be re-uploaded
        // on demand if the session is repopulated.
        chunkBuffers.values.forEach { it.close() }
        chunkBuffers.clear()
        lastSignature = 0
    }

    /**
     * Refresh dirty section buffers, then draw all cached sections.
     */
    fun render(
        context: AxionWorldRenderContext,
        color: Int,
        alpha: Int,
    ) {
        if (store.isEmpty()) return

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return

        refreshDirtyBuffers(world)
        if (chunkBuffers.isEmpty()) return

        if (AxionPreviewBlockDrawer.isDisabled()) {
            // Fallback: the GPU drawer self-disabled (e.g. uniform mismatch
            // on an unexpected MC release). Re-tessellate per frame through
            // the legacy VertexConsumer path so previews keep working.
            renderLegacy(context, world, color, alpha)
            return
        }

        AxionPreviewBlockDrawer.drawChunked(chunkBuffers, color, alpha, translationDelta)
    }

    override fun close() {
        clear()
    }

    /**
     * Tessellate every dirty section into a fresh GPU buffer. Sections that
     * have become empty get their buffers freed.
     *
     * Vertex coordinates are emitted at world-absolute positions (the drawer
     * applies the camera offset via the `DynamicTransforms` UBO). This
     * trades a tiny amount of float32 precision at extreme world coords for
     * a single mvMatrix per render pass — which lets us draw every section
     * in one pass instead of one pass per section.
     */
    private fun refreshDirtyBuffers(world: ClientWorld) {
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
                continue
            }

            val buf = chunkBuffers.computeIfAbsent(sectionKey) { AxionPreviewBuffer() }
            buf.upload(builtBuffer)
            builtBuffer.close()
        }
    }

    /**
     * Build the vertex/index data for one 16³ section's surface cells.
     * Returns null if there's nothing to draw.
     *
     * Vertices are emitted at SECTION-LOCAL coords (offset from the
     * section's world origin). The drawer applies a per-section
     * translation that, combined with the camera-rotated model-view
     * stack inherited from MC, produces correct world→camera output.
     * This mirrors vanilla terrain's per-chunk pattern exactly.
     */
    private fun buildSectionBuffer(
        sectionKey: Long,
        world: ClientWorld,
        statesByPosition: Map<Long, BlockState>,
    ): BuiltBuffer? {
        val surface = ChunkMeshTessellator.buildSectionSurface(store, sectionKey)
        if (surface.isEmpty()) return null

        val previewBlocks = ArrayList<PreviewBlockInfo>(surface.size)
        for (packed in surface) {
            val state = statesByPosition[packed] ?: continue
            if (state.isAir) continue
            previewBlocks += PreviewBlockInfo(pos = BlockPos.fromLong(packed), state = state)
        }
        if (previewBlocks.isEmpty()) return null

        val layer = RenderLayerCompat.blockTranslucentCull()
        val allocator = BufferAllocator(layer.expectedBufferSize)
        val bufferBuilder = BufferBuilder(allocator, layer.drawMode, layer.vertexFormat)
        val previewView = AxionBlockTessellator.PreviewBlockRenderView(world, statesByPosition)
        val matrices = MatrixStack()

        // Tessellate at SECTION-LOCAL coords. Passing the section's world
        // origin as the "camera position" makes the tessellator subtract
        // it from each block, leaving vertices in the 0..16 range.
        val sectionOriginX = (ChunkedBooleanStore.sectionX(sectionKey) shl 4).toDouble()
        val sectionOriginY = (ChunkedBooleanStore.sectionY(sectionKey) shl 4).toDouble()
        val sectionOriginZ = (ChunkedBooleanStore.sectionZ(sectionKey) shl 4).toDouble()
        AxionBlockTessellator.tessellateBatch(
            blocks = previewBlocks,
            world = previewView,
            matrixStack = matrices,
            consumer = bufferBuilder,
            cameraX = sectionOriginX,
            cameraY = sectionOriginY,
            cameraZ = sectionOriginZ,
            checkSides = true,
        )
        return bufferBuilder.endNullable()
    }

    /**
     * Per-frame fallback that mirrors the original Phase A+B behaviour
     * (re-tessellates surface cells through a `VertexConsumer` each frame).
     * Only invoked if [AxionPreviewBlockDrawer] has self-disabled.
     */
    private fun renderLegacy(
        context: AxionWorldRenderContext,
        world: ClientWorld,
        color: Int,
        alpha: Int,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val layer = RenderLayerCompat.blockTranslucentCull()
        val rawConsumer = context.consumers().getBuffer(layer)
        val consumer = TintedAlphaVertexConsumer(rawConsumer, alpha / 255.0f, color)
        val matrices = MatrixStack()
        val statesView = states.asMap()
        val previewView = AxionBlockTessellator.PreviewBlockRenderView(world, statesView)

        // Iterate cached section keys; tessellate each one's surface cells.
        // No frustum culling here — fallback path is best-effort.
        val previewBlocks = ArrayList<PreviewBlockInfo>(64)
        for (sectionKey in chunkBuffers.keys) {
            previewBlocks.clear()
            val surface = ChunkMeshTessellator.buildSectionSurface(store, sectionKey)
            for (packed in surface) {
                val state = statesView[packed] ?: continue
                if (state.isAir) continue
                previewBlocks += PreviewBlockInfo(pos = BlockPos.fromLong(packed), state = state)
            }
            if (previewBlocks.isEmpty()) continue
            AxionBlockTessellator.tessellateBatch(
                blocks = previewBlocks,
                world = previewView,
                matrixStack = matrices,
                consumer = consumer,
                cameraX = cameraPos.x,
                cameraY = cameraPos.y,
                cameraZ = cameraPos.z,
                checkSides = true,
            )
        }
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
}

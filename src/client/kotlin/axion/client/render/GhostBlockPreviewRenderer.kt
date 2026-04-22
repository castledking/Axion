package axion.client.render

import axion.client.network.BlockWrite
import axion.common.model.ClipboardBuffer
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

object GhostBlockPreviewRenderer {
    private const val GHOST_ALPHA: Int = 44
    private const val MAX_GHOST_BLOCKS: Int = 65536
    private const val MAX_TEXTURED_GHOST_BLOCKS: Int = 32768
    private const val DEFAULT_GHOST_COLOR: Int = 0xFFFFFFFF.toInt()

    fun maxOriginsFor(nonAirCellCount: Int): Int {
        if (nonAirCellCount <= 0) {
            return 0
        }
        return MAX_GHOST_BLOCKS / nonAirCellCount
    }

    fun render(
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int = DEFAULT_GHOST_COLOR,
        alpha: Int = GHOST_ALPHA,
        textured: Boolean = false,
        fullBlock: Boolean = false,
        scale: Float = 1.0f,
    ) {
        if (origins.isEmpty()) {
            return
        }

        val allOccupiedCells = clipboard.nonAirCells()
        if (allOccupiedCells.isEmpty()) {
            return
        }
        val occupiedCells = if (textured) {
            downsampleCells(allOccupiedCells, MAX_TEXTURED_GHOST_BLOCKS)
        } else {
            allOccupiedCells
        }
        val maxOrigins = maxOriginsFor(occupiedCells.size)
        if (maxOrigins <= 0) {
            return
        }
        val boundedOrigins = origins.asSequence().take(maxOrigins).toList()
        if (boundedOrigins.isEmpty()) {
            return
        }

        if (textured) {
            renderTextured(context, clipboard, boundedOrigins, alpha, scale, color)
            return
        }

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers()
        val fillLayer = RenderLayerCompat.debugQuads()
        val consumer = consumers.getBuffer(fillLayer)
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val shapeContext = ShapeContext.absent()

        boundedOrigins.forEach { origin ->
            occupiedCells.forEach { cell ->
                val blockPos = cell.absolutePos(origin)
                renderShapeBoxes(world, matrixStack, consumer, fillLayer, cameraPos, blockPos, cell.state, alpha, color, fullBlock, shapeContext)
            }
        }
    }

    fun renderWrites(
        context: AxionWorldRenderContext,
        writes: Collection<BlockWrite>,
        color: Int = DEFAULT_GHOST_COLOR,
        alpha: Int = GHOST_ALPHA,
        textured: Boolean = false,
        fullBlock: Boolean = false,
        scale: Float = 1.0f,
    ) {
        if (writes.isEmpty()) {
            return
        }

        val allBoundedWrites = writes.asSequence()
            .filterNot { it.state.isAir }
            .take(MAX_GHOST_BLOCKS)
            .toList()
        if (allBoundedWrites.isEmpty()) {
            return
        }
        val boundedWrites = if (textured) {
            downsampleWrites(allBoundedWrites, MAX_TEXTURED_GHOST_BLOCKS)
        } else {
            allBoundedWrites
        }

        if (textured) {
            renderTexturedWrites(context, boundedWrites, alpha, scale, color)
            return
        }

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers()
        val fillLayer = RenderLayerCompat.debugQuads()
        val consumer = consumers.getBuffer(fillLayer)
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val shapeContext = ShapeContext.absent()

        boundedWrites.forEach { write ->
            renderShapeBoxes(world, matrixStack, consumer, fillLayer, cameraPos, write.pos, write.state, alpha, color, fullBlock, shapeContext)
        }
    }

    private fun renderTextured(
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: List<BlockPos>,
        alpha: Int,
        scale: Float,
        color: Int,
    ) {
        // Phase 3 (template GPU buffer) is disabled — RenderLayer.draw() creates its own
        // render pass which doesn't composite correctly during the Fabric world render callback.
        // Phase 1 below uses context.consumers() which renders through MC's normal pipeline.

        // Phase 1: per-frame tessellation from mesh cache
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val matrixStack = context.matrices()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val alphaScale = alpha / 255.0f

        val cachedMesh = AxionPreviewMeshCache.getOrBuild(
            clipboard = clipboard,
            origins = origins,
            color = color,
            alpha = alpha,
            scale = scale,
            maxBlocks = MAX_TEXTURED_GHOST_BLOCKS,
        )

        if (cachedMesh != null && cachedMesh.blocks.isNotEmpty()) {
            val previewView = AxionBlockTessellator.TemplateBlockRenderView(world, cachedMesh.statesByPosition)
            val consumer = TintedAlphaVertexConsumer(
                context.consumers().getBuffer(RenderLayerCompat.blockTranslucentCull()),
                alphaScale,
                color,
            )
            AxionBlockTessellator.tessellateBatch(
                blocks = cachedMesh.blocks,
                world = previewView,
                matrixStack = matrixStack,
                consumer = consumer,
                cameraX = cameraPos.x,
                cameraY = cameraPos.y,
                cameraZ = cameraPos.z,
                checkSides = true,
            )
            return
        }

        // Final fallback: entity rendering
        val occupiedCells = clipboard.nonAirCells()
        val consumers = context.consumers()
        val blockRenderManager = client.blockRenderManager
        val alphaConsumers = TintedAlphaVertexConsumerProvider(consumers, alphaScale, color)
        origins.forEach { origin ->
            occupiedCells.forEach { cell ->
                val blockPos = cell.absolutePos(origin)
                matrixStack.push()
                matrixStack.translate(
                    blockPos.x - cameraPos.x,
                    blockPos.y - cameraPos.y,
                    blockPos.z - cameraPos.z,
                )
                applyScale(matrixStack, scale)
                blockRenderManager.renderBlockAsEntity(
                    cell.state,
                    matrixStack,
                    alphaConsumers,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV,
                )
                matrixStack.pop()
            }
        }
    }

    private fun renderTexturedWrites(
        context: AxionWorldRenderContext,
        writes: List<BlockWrite>,
        alpha: Int,
        scale: Float,
        color: Int,
    ) {
        // Phase 2/3 (GPU buffer paths) are disabled — RenderLayer.draw() creates its own
        // render pass which doesn't composite correctly during the Fabric world render callback.
        // Phase 1 below uses context.consumers() which renders through MC's normal pipeline.
        val blockInfos = writes.map { PreviewBlockInfo(it.pos, it.state) }

        // Phase 1: per-frame tessellation from mesh cache
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val matrixStack = context.matrices()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val alphaScale = alpha / 255.0f

        val cachedMesh = AxionPreviewMeshCache.getOrBuildForWrites(blockInfos, color, alpha)

        if (cachedMesh.blocks.isNotEmpty()) {
            val previewView = AxionBlockTessellator.TemplateBlockRenderView(world, cachedMesh.statesByPosition)
            val consumer = TintedAlphaVertexConsumer(
                context.consumers().getBuffer(RenderLayerCompat.blockTranslucentCull()),
                alphaScale,
                color,
            )
            AxionBlockTessellator.tessellateBatch(
                blocks = cachedMesh.blocks,
                world = previewView,
                matrixStack = matrixStack,
                consumer = consumer,
                cameraX = cameraPos.x,
                cameraY = cameraPos.y,
                cameraZ = cameraPos.z,
                checkSides = true,
            )
            return
        }

        // Fallback to entity rendering
        val consumers = context.consumers()
        val blockRenderManager = client.blockRenderManager
        val alphaConsumers = TintedAlphaVertexConsumerProvider(consumers, alphaScale, color)
        writes.forEach { write ->
            matrixStack.push()
            matrixStack.translate(
                write.pos.x - cameraPos.x,
                write.pos.y - cameraPos.y,
                write.pos.z - cameraPos.z,
            )
            applyScale(matrixStack, scale)
            blockRenderManager.renderBlockAsEntity(
                write.state,
                matrixStack,
                alphaConsumers,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
            )
            matrixStack.pop()
        }
    }

    private fun applyScale(matrixStack: MatrixStack, scale: Float) {
        if (scale == 1.0f) {
            return
        }
        matrixStack.translate(0.5, 0.5, 0.5)
        matrixStack.scale(scale, scale, scale)
        matrixStack.translate(-0.5, -0.5, -0.5)
    }

    private fun renderShapeBoxes(
        world: net.minecraft.client.world.ClientWorld,
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        layer: RenderLayer,
        cameraPos: net.minecraft.util.math.Vec3d,
        blockPos: BlockPos,
        state: net.minecraft.block.BlockState,
        alpha: Int,
        color: Int,
        fullBlock: Boolean,
        shapeContext: ShapeContext,
    ) {
        if (fullBlock) {
            PulsingCuboidRenderer.renderFilledBox(
                matrixStack = matrixStack,
                consumer = consumer,
                layer = layer,
                cameraPos = cameraPos,
                box = Box(
                    blockPos.x.toDouble(),
                    blockPos.y.toDouble(),
                    blockPos.z.toDouble(),
                    blockPos.x + 1.0,
                    blockPos.y + 1.0,
                    blockPos.z + 1.0,
                ),
                alpha = alpha,
                color = color,
            )
            return
        }

        val shape = state.getOutlineShape(world, blockPos, shapeContext)
        if (shape.isEmpty) return

        shape.forEachBox { minX, minY, minZ, maxX, maxY, maxZ ->
            PulsingCuboidRenderer.renderFilledBox(
                matrixStack = matrixStack,
                consumer = consumer,
                layer = layer,
                cameraPos = cameraPos,
                box = Box(
                    blockPos.x + minX,
                    blockPos.y + minY,
                    blockPos.z + minZ,
                    blockPos.x + maxX,
                    blockPos.y + maxY,
                    blockPos.z + maxZ,
                ),
                alpha = alpha,
                color = color,
            )
        }
    }

    private class TintedAlphaVertexConsumerProvider(
        private val delegate: VertexConsumerProvider,
        private val alphaScale: Float,
        private val tintColor: Int,
    ) : VertexConsumerProvider {
        override fun getBuffer(layer: RenderLayer): VertexConsumer {
            return TintedAlphaVertexConsumer(delegate.getBuffer(layer), alphaScale, tintColor)
        }
    }

    private fun downsampleCells(
        cells: List<axion.common.model.ClipboardCell>,
        maxCells: Int,
    ): List<axion.common.model.ClipboardCell> {
        if (cells.size <= maxCells) {
            return cells
        }
        return downsampleList(cells, maxCells)
    }

    private fun downsampleWrites(
        writes: List<BlockWrite>,
        maxWrites: Int,
    ): List<BlockWrite> {
        if (writes.size <= maxWrites) {
            return writes
        }
        return downsampleList(writes, maxWrites)
    }

    private fun <T> downsampleList(
        values: List<T>,
        maxValues: Int,
    ): List<T> {
        if (values.size <= maxValues) {
            return values
        }

        val result = ArrayList<T>(maxValues)
        val lastIndex = values.lastIndex
        for (index in 0 until maxValues) {
            val sourceIndex = ((index.toLong() * lastIndex) / (maxValues - 1).coerceAtLeast(1)).toInt()
            result += values[sourceIndex]
        }
        return result
    }
}

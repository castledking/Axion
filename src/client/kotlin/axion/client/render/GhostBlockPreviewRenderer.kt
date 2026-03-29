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
    private const val MAX_GHOST_BLOCKS: Int = 1536
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

        val occupiedCells = clipboard.nonAirCells()
        if (occupiedCells.isEmpty()) {
            return
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
            renderTextured(context, occupiedCells, boundedOrigins, alpha, scale, color)
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

        val boundedWrites = writes.asSequence()
            .filterNot { it.state.isAir }
            .take(MAX_GHOST_BLOCKS)
            .toList()
        if (boundedWrites.isEmpty()) {
            return
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
        occupiedCells: List<axion.common.model.ClipboardCell>,
        origins: List<BlockPos>,
        alpha: Int,
        scale: Float,
        color: Int,
    ) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val consumers = context.consumers()
        val matrixStack = context.matrices()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val blockRenderManager = client.blockRenderManager
        val alphaScale = alpha / 255.0f
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
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val consumers = context.consumers()
        val matrixStack = context.matrices()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val blockRenderManager = client.blockRenderManager
        val alphaScale = alpha / 255.0f
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

    private class TintedAlphaVertexConsumer(
        private val delegate: VertexConsumer,
        private val alphaScale: Float,
        tintColor: Int,
    ) : VertexConsumer by delegate {
        private val tintRed = (tintColor shr 16) and 0xFF
        private val tintGreen = (tintColor shr 8) and 0xFF
        private val tintBlue = tintColor and 0xFF

        override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
            delegate.color(
                tinted(red, tintRed),
                tinted(green, tintGreen),
                tinted(blue, tintBlue),
                scaledAlpha(alpha),
            )
            return this
        }

        override fun color(color: Int): VertexConsumer {
            val alpha = scaledAlpha((color ushr 24) and 0xFF)
            val red = tinted((color shr 16) and 0xFF, tintRed)
            val green = tinted((color shr 8) and 0xFF, tintGreen)
            val blue = tinted(color and 0xFF, tintBlue)
            delegate.color((alpha shl 24) or (red shl 16) or (green shl 8) or blue)
            return this
        }

        private fun scaledAlpha(alpha: Int): Int {
            return (alpha * alphaScale).toInt().coerceIn(0, 255)
        }

        private fun tinted(channel: Int, tint: Int): Int {
            val lifted = mix(channel, 255, 0.5f)
            return mix(lifted, tint, 0.35f)
        }

        private fun mix(from: Int, to: Int, amount: Float): Int {
            return (from + (to - from) * amount).toInt().coerceIn(0, 255)
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
}

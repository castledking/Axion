package axion.client.render

import axion.client.network.BlockWrite
import axion.common.model.ClipboardBuffer
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.render.model.BlockModelPart
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.random.Random

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
        context: WorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int = DEFAULT_GHOST_COLOR,
        alpha: Int = GHOST_ALPHA,
        textured: Boolean = false,
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
            renderTextured(context, occupiedCells, boundedOrigins, alpha)
            return
        }

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers()
        val consumer = consumers.getBuffer(RenderLayers.debugFilledBox())
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val shapeContext = ShapeContext.absent()

        boundedOrigins.forEach { origin ->
            occupiedCells.forEach { cell ->
                val blockPos = cell.absolutePos(origin)
                val shape = cell.state.getOutlineShape(world, blockPos, shapeContext)
                if (shape.isEmpty) {
                    return@forEach
                }

                shape.forEachBox { minX, minY, minZ, maxX, maxY, maxZ ->
                    PulsingCuboidRenderer.renderFilledBox(
                        matrixStack = matrixStack,
                        consumer = consumer,
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
        }
    }

    fun renderWrites(
        context: WorldRenderContext,
        writes: Collection<BlockWrite>,
        color: Int = DEFAULT_GHOST_COLOR,
        alpha: Int = GHOST_ALPHA,
        textured: Boolean = false,
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
            renderTexturedWrites(context, boundedWrites, alpha)
            return
        }

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers()
        val consumer = consumers.getBuffer(RenderLayers.debugFilledBox())
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val shapeContext = ShapeContext.absent()

        boundedWrites.forEach { write ->
            val blockPos = write.pos
            val shape = write.state.getOutlineShape(world, blockPos, shapeContext)
            if (shape.isEmpty) {
                return@forEach
            }

            shape.forEachBox { minX, minY, minZ, maxX, maxY, maxZ ->
                PulsingCuboidRenderer.renderFilledBox(
                    matrixStack = matrixStack,
                    consumer = consumer,
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
    }

    private fun renderTextured(
        context: WorldRenderContext,
        occupiedCells: List<axion.common.model.ClipboardCell>,
        origins: List<BlockPos>,
        alpha: Int,
    ) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val consumers = context.consumers()
        val matrixStack = context.matrices()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val blockRenderManager = client.blockRenderManager
        val alphaScale = alpha / 255.0f
        val consumer = AlphaVertexConsumer(consumers.getBuffer(RenderLayers.translucentMovingBlock()), alphaScale)
        val random = Random.create(0L)

        origins.forEach { origin ->
            occupiedCells.forEach { cell ->
                val blockPos = cell.absolutePos(origin)
                val model = blockRenderManager.getModel(cell.state)
                val parts = mutableListOf<BlockModelPart>()
                random.setSeed(cell.state.hashCode().toLong())
                model.addParts(random, parts)
                if (parts.isEmpty()) {
                    return@forEach
                }
                matrixStack.push()
                matrixStack.translate(
                    blockPos.x - cameraPos.x,
                    blockPos.y - cameraPos.y,
                    blockPos.z - cameraPos.z,
                )
                renderTexturedParts(
                    matrixStack = matrixStack,
                    consumer = consumer,
                    parts = parts,
                    light = LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    overlay = OverlayTexture.DEFAULT_UV,
                )
                matrixStack.pop()
            }
        }
    }

    private fun renderTexturedWrites(
        context: WorldRenderContext,
        writes: List<BlockWrite>,
        alpha: Int,
    ) {
        val client = MinecraftClient.getInstance()
        val consumers = context.consumers()
        val matrixStack = context.matrices()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val blockRenderManager = client.blockRenderManager
        val alphaScale = alpha / 255.0f
        val consumer = AlphaVertexConsumer(consumers.getBuffer(RenderLayers.translucentMovingBlock()), alphaScale)
        val random = Random.create(0L)

        writes.forEach { write ->
            val model = blockRenderManager.getModel(write.state)
            val parts = mutableListOf<BlockModelPart>()
            random.setSeed(write.state.hashCode().toLong())
            model.addParts(random, parts)
            if (parts.isEmpty()) {
                return@forEach
            }
            matrixStack.push()
            matrixStack.translate(
                write.pos.x - cameraPos.x,
                write.pos.y - cameraPos.y,
                write.pos.z - cameraPos.z,
            )
            renderTexturedParts(
                matrixStack = matrixStack,
                consumer = consumer,
                parts = parts,
                light = LightmapTextureManager.MAX_LIGHT_COORDINATE,
                overlay = OverlayTexture.DEFAULT_UV,
            )
            matrixStack.pop()
        }
    }

    private fun renderTexturedParts(
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        parts: List<BlockModelPart>,
        light: Int,
        overlay: Int,
    ) {
        val entry = matrixStack.peek()
        parts.forEach { part ->
            part.getQuads(null).forEach { quad ->
                consumer.quad(entry, quad, 1.0f, 1.0f, 1.0f, 1.0f, light, overlay)
            }
            net.minecraft.util.math.Direction.entries.forEach { direction ->
                part.getQuads(direction).forEach { quad ->
                    consumer.quad(entry, quad, 1.0f, 1.0f, 1.0f, 1.0f, light, overlay)
                }
            }
        }
    }

    private class AlphaVertexConsumer(
        private val delegate: VertexConsumer,
        private val alphaScale: Float,
    ) : VertexConsumer by delegate {
        override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
            delegate.color(red, green, blue, scaledAlpha(alpha))
            return this
        }

        override fun color(color: Int): VertexConsumer {
            val alpha = scaledAlpha((color ushr 24) and 0xFF)
            val rgb = color and 0x00FFFFFF
            delegate.color((alpha shl 24) or rgb)
            return this
        }

        private fun scaledAlpha(alpha: Int): Int {
            return (alpha * alphaScale).toInt().coerceIn(0, 255)
        }
    }
}

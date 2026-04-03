package axion.client.render

import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i
import net.minecraft.world.BlockRenderView
import net.minecraft.world.biome.ColorResolver
import net.minecraft.world.chunk.light.LightingProvider

object PreviewShellBlockRenderer {
    private const val MAX_RENDERED_FACES: Int = 1024

    fun render(
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int,
        alpha: Int,
    ): Boolean {
        // The newer shell backend is currently unstable and can drop the preview entirely.
        // Force the caller onto the ghost fallback until the bespoke region renderer is fixed.
        return false

        if (origins.isEmpty()) {
            return false
        }

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return false
        val camera = client.gameRenderer.camera ?: return false
        val region = ChunkedPreviewRegion.getOrBuild(
            clipboard = clipboard,
            origins = origins,
            maxQuads = MAX_RENDERED_FACES,
        )
        val quads = region.chunks.values.asSequence().flatMap { it.quads.asSequence() }.toList()
        if (quads.isEmpty()) {
            return false
        }

        val shapeContext = ShapeContext.absent()
        val consumer = TintedAlphaVertexConsumer(
            context.consumers().getBuffer(RenderLayerCompat.blockTranslucentCull()),
            alpha / 255.0f,
            color,
        )
        val matrixStack = context.matrices()
        val cameraPos = camera.cameraPos

        var renderedAnyFace = false
        val entry = matrixStack.peek()
        quads.forEach { quad ->
            val bounds = quad.bounds
            val blockPos = BlockPos(
                kotlin.math.floor(bounds.minX).toInt(),
                kotlin.math.floor(bounds.minY).toInt(),
                kotlin.math.floor(bounds.minZ).toInt(),
            )
            val previewView = PreviewBlockRenderView(
                world = world,
                origin = blockPos,
                size = Vec3i(1, 1, 1),
                statesByOffset = mapOf(0L to quad.state),
            )
            val shape = quad.state.getOutlineShape(previewView, blockPos, shapeContext)
            if (shape.isEmpty && quad.state.renderType != BlockRenderType.MODEL) {
                return@forEach
            }
            val sprite = client.blockRenderManager.getModel(quad.state).particleSprite()
            emitTexturedFace(
                consumer = consumer,
                entry = entry,
                face = quad.face,
                minX = (bounds.minX - cameraPos.x).toFloat(),
                minY = (bounds.minY - cameraPos.y).toFloat(),
                minZ = (bounds.minZ - cameraPos.z).toFloat(),
                maxX = (bounds.maxX - cameraPos.x).toFloat(),
                maxY = (bounds.maxY - cameraPos.y).toFloat(),
                maxZ = (bounds.maxZ - cameraPos.z).toFloat(),
                u0 = sprite.minU,
                v0 = sprite.minV,
                u1 = sprite.maxU,
                v1 = sprite.maxV,
            )
            renderedAnyFace = true
        }

        return renderedAnyFace
    }

    private fun emitTexturedFace(
        consumer: VertexConsumer,
        entry: MatrixStack.Entry,
        face: Direction,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
    ) {
        when (face) {
            Direction.NORTH -> emitQuad(
                consumer,
                entry,
                minX, minY, minZ, u0, v1,
                maxX, minY, minZ, u1, v1,
                maxX, maxY, minZ, u1, v0,
                minX, maxY, minZ, u0, v0,
                0f, 0f, -1f,
            )

            Direction.SOUTH -> emitQuad(
                consumer,
                entry,
                minX, minY, maxZ, u1, v1,
                minX, maxY, maxZ, u1, v0,
                maxX, maxY, maxZ, u0, v0,
                maxX, minY, maxZ, u0, v1,
                0f, 0f, 1f,
            )

            Direction.WEST -> emitQuad(
                consumer,
                entry,
                minX, minY, minZ, u1, v1,
                minX, maxY, minZ, u1, v0,
                minX, maxY, maxZ, u0, v0,
                minX, minY, maxZ, u0, v1,
                -1f, 0f, 0f,
            )

            Direction.EAST -> emitQuad(
                consumer,
                entry,
                maxX, minY, minZ, u0, v1,
                maxX, minY, maxZ, u1, v1,
                maxX, maxY, maxZ, u1, v0,
                maxX, maxY, minZ, u0, v0,
                1f, 0f, 0f,
            )

            Direction.UP -> emitQuad(
                consumer,
                entry,
                minX, maxY, minZ, u0, v1,
                maxX, maxY, minZ, u1, v1,
                maxX, maxY, maxZ, u1, v0,
                minX, maxY, maxZ, u0, v0,
                0f, 1f, 0f,
            )

            Direction.DOWN -> emitQuad(
                consumer,
                entry,
                minX, minY, minZ, u0, v1,
                minX, minY, maxZ, u0, v0,
                maxX, minY, maxZ, u1, v0,
                maxX, minY, minZ, u1, v1,
                0f, -1f, 0f,
            )
        }
    }

    private fun emitQuad(
        consumer: VertexConsumer,
        entry: MatrixStack.Entry,
        x1: Float,
        y1: Float,
        z1: Float,
        u1: Float,
        v1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        u2: Float,
        v2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        u3: Float,
        v3: Float,
        x4: Float,
        y4: Float,
        z4: Float,
        u4: Float,
        v4: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
    ) {
        emitVertex(consumer, entry, x1, y1, z1, u1, v1, normalX, normalY, normalZ)
        emitVertex(consumer, entry, x2, y2, z2, u2, v2, normalX, normalY, normalZ)
        emitVertex(consumer, entry, x3, y3, z3, u3, v3, normalX, normalY, normalZ)
        emitVertex(consumer, entry, x4, y4, z4, u4, v4, normalX, normalY, normalZ)
    }

    private fun emitVertex(
        consumer: VertexConsumer,
        entry: MatrixStack.Entry,
        x: Float,
        y: Float,
        z: Float,
        u: Float,
        v: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
    ) {
        consumer
            .vertex(entry, x, y, z)
            .color(255, 255, 255, 255)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(entry, normalX, normalY, normalZ)
    }

    private class PreviewBlockRenderView(
        private val world: net.minecraft.client.world.ClientWorld,
        private val origin: BlockPos,
        private val size: Vec3i,
        private val statesByOffset: Map<Long, BlockState>,
    ) : BlockRenderView {
        override fun getBlockEntity(pos: BlockPos): BlockEntity? {
            return if (contains(pos)) null else world.getBlockEntity(pos)
        }

        override fun getBlockState(pos: BlockPos): BlockState {
            if (!contains(pos)) {
                return world.getBlockState(pos)
            }

            return statesByOffset[
                BlockPos.asLong(
                    pos.x - origin.x,
                    pos.y - origin.y,
                    pos.z - origin.z,
                ),
            ] ?: Blocks.AIR.defaultState
        }

        override fun getFluidState(pos: BlockPos) = getBlockState(pos).fluidState

        override fun getHeight(): Int = world.height

        override fun getBottomY(): Int = world.bottomY

        override fun getBrightness(direction: Direction, shaded: Boolean): Float = world.getBrightness(direction, shaded)

        override fun getLightingProvider(): LightingProvider = world.lightingProvider

        override fun getColor(pos: BlockPos, colorResolver: ColorResolver): Int = world.getColor(pos, colorResolver)

        private fun contains(pos: BlockPos): Boolean {
            val offsetX = pos.x - origin.x
            val offsetY = pos.y - origin.y
            val offsetZ = pos.z - origin.z
            return offsetX in 0 until size.x &&
                offsetY in 0 until size.y &&
                offsetZ in 0 until size.z
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
            val lifted = mix(channel, 255, 0.35f)
            return mix(lifted, tint, 0.25f)
        }

        private fun mix(from: Int, to: Int, amount: Float): Int {
            return (from + (to - from) * amount).toInt().coerceIn(0, 255)
        }
    }
}

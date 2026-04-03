package axion.client.render

import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.BlockRenderView
import net.minecraft.world.biome.ColorResolver
import net.minecraft.world.chunk.light.LightingProvider

object PreviewBlockTessellator {
    fun render(
        context: AxionWorldRenderContext,
        region: ChunkedPreviewRegion,
        color: Int,
        alpha: Int,
    ): Boolean {
        if (region.surfaceBlocks.isEmpty()) {
            return false
        }

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return false
        val camera = client.gameRenderer.camera ?: return false
        val blockRenderManager = client.blockRenderManager
        val previewView = PreviewRegionBlockRenderView(world, region.statesByPosition)
        val matrices = context.matrices()
        val cameraPos = camera.cameraPos
        val consumer = TintedAlphaVertexConsumer(
            context.consumers().getBuffer(RenderLayerCompat.blockTranslucentCull()),
            alpha / 255.0f,
            color,
        )

        var rendered = false
        region.surfaceBlocks.forEach { block ->
            val state = block.state
            if (state.isAir || state.renderType != BlockRenderType.MODEL) {
                return@forEach
            }

            matrices.push()
            matrices.translate(
                block.pos.x - cameraPos.x,
                block.pos.y - cameraPos.y,
                block.pos.z - cameraPos.z,
            )
            blockRenderManager.renderBlock(
                state,
                block.pos,
                previewView,
                matrices,
                consumer,
                true,
                emptyList(),
            )
            matrices.pop()
            rendered = true
        }

        return rendered
    }

    private class PreviewRegionBlockRenderView(
        private val world: net.minecraft.client.world.ClientWorld,
        private val statesByPosition: Map<Long, BlockState>,
    ) : BlockRenderView {
        override fun getBlockEntity(pos: BlockPos): BlockEntity? {
            return if (statesByPosition.containsKey(pos.asLong())) null else world.getBlockEntity(pos)
        }

        override fun getBlockState(pos: BlockPos): BlockState {
            return statesByPosition[pos.asLong()] ?: world.getBlockState(pos)
        }

        override fun getFluidState(pos: BlockPos) = getBlockState(pos).fluidState

        override fun getHeight(): Int = world.height

        override fun getBottomY(): Int = world.bottomY

        override fun getBrightness(direction: Direction, shaded: Boolean): Float = world.getBrightness(direction, shaded)

        override fun getLightingProvider(): LightingProvider = world.lightingProvider

        override fun getColor(pos: BlockPos, colorResolver: ColorResolver): Int = world.getColor(pos, colorResolver)
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

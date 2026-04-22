package axion.client.render

import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.model.BlockModelPart
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
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
        val random = Random.create()
        val parts = ArrayList<BlockModelPart>(16)
        region.surfaceBlocks.forEach { block ->
            val state = block.state
            if (state.isAir || state.renderType != BlockRenderType.MODEL) {
                return@forEach
            }

            val model = blockRenderManager.getModel(state)
            parts.clear()
            random.setSeed(state.getRenderingSeed(block.pos))
            model.addParts(random, parts)
            if (parts.isEmpty()) {
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
                parts,
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
}

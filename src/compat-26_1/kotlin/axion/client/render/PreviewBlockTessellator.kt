package axion.client.render

import axion.client.compat.CameraAccess
import axion.client.render.gpu.PreviewOcclusionCompat
import axion.client.render.gpu.PreviewOcclusionPolicy
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BlockRenderManager
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.BlockQuadOutput
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LightType
import net.minecraft.world.biome.ColorResolver
import net.minecraft.world.chunk.light.LightingProvider
import net.minecraft.world.level.CardinalLighting
import com.mojang.blaze3d.vertex.QuadInstance
import net.minecraft.client.resources.model.geometry.BakedQuad

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
        val camera = client.gameRenderer.camera
        val blockRenderer = BlockRenderManager(true, true, client.blockColors)
        val modelSet = client.modelManager.blockStateModelSet
        val cameraPos = CameraAccess.getPos(camera)
        val consumer = TintedAlphaVertexConsumer(
            context.consumers().getBuffer(RenderLayerCompat.blockTranslucentCull()),
            alpha / 255.0f,
            color,
        )

        var rendered = false
        region.surfaceBlocks.forEach { block ->
            val state = block.state
            if (state.isAir || state.renderTypeCompat != BlockRenderType.MODEL) {
                return@forEach
            }

            val model = modelSet.get(state)
            val previewView = PreviewRegionBlockRenderView(world, region.statesByPosition, block.pos)
            val output = BlockQuadOutput { x: Float, y: Float, z: Float, quad: BakedQuad, quadInstance: QuadInstance ->
                consumer.putBlockBakedQuad(
                    x - cameraPos.x.toFloat(),
                    y - cameraPos.y.toFloat(),
                    z - cameraPos.z.toFloat(),
                    quad,
                    quadInstance,
                )
            }

            blockRenderer.tesselateBlock(
                output,
                block.pos.x.toFloat(),
                block.pos.y.toFloat(),
                block.pos.z.toFloat(),
                previewView,
                block.pos,
                state,
                model,
                state.getRenderingSeedCompat(block.pos),
            )
            rendered = true
        }

        return rendered
    }

    private class PreviewRegionBlockRenderView(
        private val world: net.minecraft.client.world.ClientWorld,
        private val statesByPosition: Map<Long, BlockState>,
        private val renderingPos: BlockPos? = null,
    ) : BlockAndTintGetter {
        private val airState: BlockState = Blocks.AIR.defaultState

        override fun getBlockEntity(pos: net.minecraft.core.BlockPos): BlockEntity? {
            return null
        }

        override fun getBlockState(pos: net.minecraft.core.BlockPos): BlockState {
            if (renderingPos != null && PreviewOcclusionPolicy.isDirectNeighbor(
                    pos.x, pos.y, pos.z,
                    renderingPos.x, renderingPos.y, renderingPos.z,
                )
            ) {
                val neighborState = statesByPosition[pos.asLong()]
                if (PreviewOcclusionPolicy.isFaceExposed(neighborState, PreviewOcclusionCompat::isOpaqueFullCube)) {
                    return airState
                }
            }
            return statesByPosition[pos.asLong()] ?: airState
        }

        override fun getFluidState(pos: net.minecraft.core.BlockPos) = getBlockState(pos).fluidState

        override fun getHeight(): Int = world.height

        override fun getMinY(): Int = world.minY

        override fun getLightEngine(): LightingProvider = world.lightEngine

        override fun getBrightness(layer: LightType, pos: net.minecraft.core.BlockPos): Int = 15

        override fun cardinalLighting(): CardinalLighting = world.cardinalLighting()

        override fun getBlockTint(pos: net.minecraft.core.BlockPos, colorResolver: ColorResolver): Int {
            return world.getBlockTint(pos, colorResolver)
        }
    }
}

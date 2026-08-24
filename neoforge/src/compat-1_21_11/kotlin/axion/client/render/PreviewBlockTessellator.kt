package axion.client.render
import axion.client.compat.CameraAccess

import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.BlockModelPart
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.ColorResolver
import net.minecraft.world.level.lighting.LevelLightEngine

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

        val client = Minecraft.getInstance()
        val world = client.level ?: return false
        val camera = client.gameRenderer.mainCamera ?: return false
        val blockRenderManager = client.blockRenderer
        val previewView = PreviewRegionBlockRenderView(world, region.statesByPosition)
        val matrices = context.matrices()
        val cameraPos = CameraAccess.getPos(camera)
        val consumer = TintedAlphaVertexConsumer(
            context.consumers().getBuffer(RenderLayerCompat.blockTranslucentCull()),
            alpha / 255.0f,
            color,
        )

        var rendered = false
        val random = RandomSource.create()
        val parts = ArrayList<BlockModelPart>(16)
        region.surfaceBlocks.forEach { block ->
            val state = block.state
            if (state.isAir || state.getRenderShape() != RenderShape.MODEL) {
                return@forEach
            }

            val model = blockRenderManager.getBlockModel(state)
            parts.clear()
            random.setSeed(state.getSeed(block.pos))
            model.collectParts(random, parts)
            if (parts.isEmpty()) {
                return@forEach
            }

            matrices.pushPose()
            matrices.translate(
                block.pos.x - cameraPos.x,
                block.pos.y - cameraPos.y,
                block.pos.z - cameraPos.z,
            )
            blockRenderManager.renderBatched(
                state,
                block.pos,
                previewView,
                matrices,
                consumer,
                true,
                parts,
            )
            matrices.popPose()
            rendered = true
        }

        return rendered
    }

    private class PreviewRegionBlockRenderView(
        private val world: net.minecraft.client.multiplayer.ClientLevel,
        private val statesByPosition: Map<Long, BlockState>,
    ) : BlockAndTintGetter {
        override fun getBlockEntity(pos: BlockPos): BlockEntity? {
            return if (statesByPosition.contains(pos.asLong())) null else world.getBlockEntity(pos)
        }

        override fun getBlockState(pos: BlockPos): BlockState {
            return statesByPosition[pos.asLong()] ?: world.getBlockState(pos)
        }

        override fun getFluidState(pos: BlockPos) = getBlockState(pos).fluidState

        override fun getHeight(): Int = world.height

        override fun getMinY(): Int = world.minY

        override fun getShade(direction: Direction, shaded: Boolean): Float = world.getShade(direction, shaded)

        override fun getLightEngine(): net.minecraft.world.level.lighting.LevelLightEngine = world.lightEngine

        override fun getBrightness(type: net.minecraft.world.level.LightLayer, pos: BlockPos): Int = world.getBrightness(type, pos)

        override fun getRawBrightness(pos: BlockPos, ambientDarkness: Int): Int = world.getRawBrightness(pos, ambientDarkness)

        override fun canSeeSky(pos: BlockPos): Boolean = world.canSeeSky(pos)

        override fun getBlockTint(pos: BlockPos, colorResolver: ColorResolver): Int = world.getBlockTint(pos, colorResolver)
    }
}

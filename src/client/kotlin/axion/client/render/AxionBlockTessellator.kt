package axion.client.render

import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.model.BlockModelPart
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import net.minecraft.world.BlockRenderView
import net.minecraft.world.biome.ColorResolver
import net.minecraft.world.chunk.light.LightingProvider

/**
 * Tessellates blocks directly using blockRenderManager.renderBlock() instead of
 * the expensive renderBlockAsEntity() path. Caches BakedModel lookups and provides
 * a reusable BlockRenderView for preview regions.
 *
 * Inspired by Axiom's BlockTessellator: avoids entity-rendering overhead,
 * caches model references, and supports ambient occlusion via the block pipeline.
 */
object AxionBlockTessellator {

    /** Thread-local random for getting block model parts (avoids allocation per block). */
    private val threadLocalRandom: ThreadLocal<Random> = ThreadLocal.withInitial { Random.create() }

    /** Reusable list for collecting model parts (avoids allocation per block). */
    private val threadLocalParts: ThreadLocal<MutableList<BlockModelPart>> = ThreadLocal.withInitial { ArrayList(16) }

    fun clearCache() {
        // No-op: renderBlock handles model lookup internally
    }

    /**
     * Tessellate a single block into a VertexConsumer using the block render pipeline.
     * This bypasses the entity-rendering overhead of renderBlockAsEntity().
     *
     * Gets the block's model parts via BlockStateModel.addParts() and passes them
     * to renderBlock(). Previously passed emptyList() which produced zero vertices.
     */
    fun tessellateBlock(
        state: BlockState,
        pos: BlockPos,
        world: BlockRenderView,
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        checkSides: Boolean = true,
    ): Boolean {
        if (state.isAir || state.renderType != BlockRenderType.MODEL) {
            return false
        }
        val blockRenderManager = MinecraftClient.getInstance().blockRenderManager
        val model = blockRenderManager.getModel(state)
        val random = threadLocalRandom.get()
        val parts = threadLocalParts.get()
        parts.clear()
        random.setSeed(state.getRenderingSeed(pos))
        model.addParts(random, parts)
        if (parts.isEmpty()) {
            return false
        }
        blockRenderManager.renderBlock(state, pos, world, matrixStack, consumer, checkSides, parts)
        return true
    }

    /**
     * Batch-tessellate multiple blocks into a single VertexConsumer.
     * Translates the matrix stack per block position relative to the camera.
     */
    fun tessellateBatch(
        blocks: List<PreviewBlockInfo>,
        world: BlockRenderView,
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        checkSides: Boolean = true,
    ): Int {
        var rendered = 0
        for (block in blocks) {
            matrixStack.push()
            matrixStack.translate(
                block.pos.x - cameraX,
                block.pos.y - cameraY,
                block.pos.z - cameraZ,
            )
            if (tessellateBlock(block.state, block.pos, world, matrixStack, consumer, checkSides)) {
                rendered++
            }
            matrixStack.pop()
        }
        return rendered
    }

    /**
     * A BlockRenderView that overlays preview block states onto the real world.
     * Positions in the statesByPosition map return the preview state; all others
     * fall through to the actual ClientWorld.
     */
    class PreviewBlockRenderView(
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

        override fun getBrightness(direction: Direction, shaded: Boolean): Float =
            world.getBrightness(direction, shaded)

        override fun getLightingProvider(): LightingProvider = world.lightingProvider

        override fun getColor(pos: BlockPos, colorResolver: ColorResolver): Int =
            world.getColor(pos, colorResolver)
    }

    /**
     * A BlockRenderView for template tessellation at offset positions.
     * Returns AIR for non-clipboard positions so face culling and AO work
     * correctly regardless of real-world blocks at those coordinates.
     */
    class TemplateBlockRenderView(
        private val world: net.minecraft.client.world.ClientWorld,
        private val statesByPosition: Map<Long, BlockState>,
    ) : BlockRenderView {
        private val airState: BlockState = net.minecraft.block.Blocks.AIR.defaultState

        override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

        override fun getBlockState(pos: BlockPos): BlockState {
            return statesByPosition[pos.asLong()] ?: airState
        }

        override fun getFluidState(pos: BlockPos) = getBlockState(pos).fluidState

        override fun getHeight(): Int = world.height

        override fun getBottomY(): Int = world.bottomY

        override fun getBrightness(direction: Direction, shaded: Boolean): Float =
            world.getBrightness(direction, shaded)

        override fun getLightingProvider(): LightingProvider = world.lightingProvider

        override fun getColor(pos: BlockPos, colorResolver: ColorResolver): Int =
            world.getColor(pos, colorResolver)
    }
}

data class PreviewBlockInfo(
    val pos: BlockPos,
    val state: BlockState,
)

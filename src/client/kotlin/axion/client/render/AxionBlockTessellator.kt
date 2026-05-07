package axion.client.render

import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.model.BlockModelPart
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import net.minecraft.world.BlockRenderView
import net.minecraft.world.LightType
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
        if (state.isAir) {
            return false
        }
        val blockRenderManager = MinecraftClient.getInstance().blockRenderManager
        var rendered = false

        if (state.renderType == BlockRenderType.MODEL) {
            val model = blockRenderManager.getModel(state)
            val random = threadLocalRandom.get()
            val parts = threadLocalParts.get()
            parts.clear()
            random.setSeed(state.getRenderingSeed(pos))
            model.addParts(random, parts)
            if (parts.isNotEmpty()) {
                // For grass blocks, render each part individually with checkSides=false
                // This bypasses the grass block model's built-in top-face culling
                if (state.block == Blocks.GRASS_BLOCK) {
                    for (part in parts) {
                        blockRenderManager.renderBlock(state, pos, world, matrixStack, consumer, false, listOf(part))
                    }
                } else {
                    blockRenderManager.renderBlock(state, pos, world, matrixStack, consumer, checkSides, parts)
                }
                rendered = true
            }
        }

        val fluidState = state.fluidState
        if (!fluidState.isEmpty) {
            blockRenderManager.renderFluid(pos, world, consumer, state, fluidState)
            rendered = true
        }

        return rendered
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
        checkSidesFor: ((PreviewBlockInfo) -> Boolean)? = null,
        scale: Float = 1.0f,
    ): Int {
        var rendered = 0
        for (block in blocks) {
            matrixStack.push()
            matrixStack.translate(
                block.pos.x - cameraX,
                block.pos.y - cameraY,
                block.pos.z - cameraZ,
            )
            if (scale != 1.0f) {
                matrixStack.translate(0.5, 0.5, 0.5)
                matrixStack.scale(scale, scale, scale)
                matrixStack.translate(-0.5, -0.5, -0.5)
            }
            val blockCheckSides = checkSidesFor?.invoke(block) ?: checkSides
            // Create a fresh view for each block to pass the rendering position
            val blockWorld = when (world) {
                is PreviewBlockRenderView -> PreviewBlockRenderView(world.world, world.statesByPosition, block.pos)
                is TemplateBlockRenderView -> TemplateBlockRenderView(world.world, world.statesByPosition, block.pos)
                else -> world
            }
            if (tessellateBlock(block.state, block.pos, blockWorld, matrixStack, consumer, blockCheckSides)) {
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
        val world: net.minecraft.client.world.ClientWorld,
        val statesByPosition: Map<Long, BlockState>,
        private val renderingPos: BlockPos? = null,
    ) : BlockRenderView {
        private val airState: BlockState = Blocks.AIR.defaultState

        override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

        override fun getBlockState(pos: BlockPos): BlockState {
            // When rendering a block, if the position directly above it is a non-opaque block
            // (short grass, crops, flowers, etc.), return AIR for that position so the
            // block renderer doesn't cull its top face.
            if (renderingPos != null && pos == renderingPos.up()) {
                val aboveState = statesByPosition[pos.asLong()]
                if (aboveState != null && !aboveState.isOpaqueFullCube) {
                    return airState
                }
            }
            return statesByPosition[pos.asLong()] ?: airState
        }

        override fun getFluidState(pos: BlockPos) = getBlockState(pos).fluidState

        override fun getHeight(): Int = world.height

        override fun getBottomY(): Int = world.bottomY

        override fun getBrightness(direction: Direction, shaded: Boolean): Float =
            previewBrightness(direction, shaded)

        override fun getLightingProvider(): LightingProvider = world.lightingProvider

        override fun getLightLevel(type: LightType, pos: BlockPos): Int = 15

        override fun getBaseLightLevel(pos: BlockPos, ambientDarkness: Int): Int = 15

        override fun isSkyVisible(pos: BlockPos): Boolean = true

        override fun getColor(pos: BlockPos, colorResolver: ColorResolver): Int =
            world.getColor(pos, colorResolver)
    }

    /**
     * A BlockRenderView for template tessellation at offset positions.
     * Returns AIR for non-clipboard positions so face culling and AO work
     * correctly regardless of real-world blocks at those coordinates.
     */
    class TemplateBlockRenderView(
        val world: net.minecraft.client.world.ClientWorld,
        val statesByPosition: Map<Long, BlockState>,
        private val renderingPos: BlockPos? = null,
    ) : BlockRenderView {
        private val airState: BlockState = Blocks.AIR.defaultState

        override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

        override fun getBlockState(pos: BlockPos): BlockState {
            // When rendering a block, if the position directly above it is a non-opaque block
            // (short grass, crops, flowers, etc.), return AIR for that position so the
            // block renderer doesn't cull its top face.
            if (renderingPos != null && pos == renderingPos.up()) {
                val aboveState = statesByPosition[pos.asLong()]
                if (aboveState != null && !aboveState.isOpaqueFullCube) {
                    return airState
                }
            }
            return statesByPosition[pos.asLong()] ?: airState
        }

        override fun getFluidState(pos: BlockPos) = getBlockState(pos).fluidState

        override fun getHeight(): Int = world.height

        override fun getBottomY(): Int = world.bottomY

        override fun getBrightness(direction: Direction, shaded: Boolean): Float =
            previewBrightness(direction, shaded)

        override fun getLightingProvider(): LightingProvider = world.lightingProvider

        override fun getLightLevel(type: LightType, pos: BlockPos): Int = 15

        override fun getBaseLightLevel(pos: BlockPos, ambientDarkness: Int): Int = 15

        override fun isSkyVisible(pos: BlockPos): Boolean = true

        override fun getColor(pos: BlockPos, colorResolver: ColorResolver): Int =
            world.getColor(pos, colorResolver)
    }

    private fun previewBrightness(direction: Direction, shaded: Boolean): Float {
        if (!shaded) return 1.0f
        return when (direction) {
            Direction.DOWN -> 0.5f
            Direction.UP -> 1.0f
            Direction.NORTH, Direction.SOUTH -> 0.8f
            Direction.WEST, Direction.EAST -> 0.6f
        }
    }
}

data class PreviewBlockInfo(
    val pos: BlockPos,
    val state: BlockState,
)

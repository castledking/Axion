package axion.client.render

import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
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
 * Avoids entity-rendering overhead,
 * caches model references, and supports ambient occlusion via the block pipeline.
 */
object AxionBlockTessellator {

    /** Thread-local random for getting block model parts (avoids allocation per block). */
    private val threadLocalRandom: ThreadLocal<Random> = ThreadLocal.withInitial { Random.create() }

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
        cameraX: Double = 0.0,
        cameraY: Double = 0.0,
        cameraZ: Double = 0.0,
        scale: Float = 1.0f,
    ): Boolean {
        if (state.isAir) {
            return false
        }
        val blockRenderManager = MinecraftClient.getInstance().blockRenderManager
        var rendered = false

        if (state.renderType == BlockRenderType.MODEL) {
            val random = threadLocalRandom.get()
            random.setSeed(state.getRenderingSeed(pos))
            blockRenderManager.renderBlock(state, pos, world, matrixStack, consumer, checkSides, random)
            rendered = true
        }

        val fluidState = state.fluidState
        if (!fluidState.isEmpty) {
            blockRenderManager.renderFluid(
                pos,
                world,
                FluidOffsetVertexConsumer(consumer, pos, cameraX, cameraY, cameraZ, scale),
                state,
                fluidState,
            )
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
            if (tessellateBlock(block.state, block.pos, blockWorld, matrixStack, consumer, blockCheckSides, cameraX, cameraY, cameraZ, scale)) {
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

    private class FluidOffsetVertexConsumer(
        private val delegate: VertexConsumer,
        private val pos: BlockPos,
        private val cameraX: Double,
        private val cameraY: Double,
        private val cameraZ: Double,
        private val scale: Float,
    ) : VertexConsumer {
        override fun vertex(x: Float, y: Float, z: Float): VertexConsumer {
            delegate.vertex(transformX(x), transformY(y), transformZ(z))
            return this
        }

        override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
            delegate.color(red, green, blue, alpha)
            return this
        }

        override fun color(color: Int): VertexConsumer {
            delegate.color(color)
            return this
        }

        override fun texture(u: Float, v: Float): VertexConsumer {
            delegate.texture(u, v)
            return this
        }

        override fun overlay(u: Int, v: Int): VertexConsumer {
            delegate.overlay(u, v)
            return this
        }

        override fun light(u: Int, v: Int): VertexConsumer {
            delegate.light(u, v)
            return this
        }

        override fun normal(x: Float, y: Float, z: Float): VertexConsumer {
            delegate.normal(x, y, z)
            return this
        }

        @Suppress("NOTHING_TO_OVERRIDE", "ACCIDENTAL_OVERRIDE")
        override fun lineWidth(w: Float): VertexConsumer {
            lineWidthMethod?.invoke(delegate, w)
            return this
        }

        override fun vertex(
            x: Float,
            y: Float,
            z: Float,
            color: Int,
            u: Float,
            v: Float,
            overlay: Int,
            light: Int,
            nx: Float,
            ny: Float,
            nz: Float,
        ) {
            delegate.vertex(transformX(x), transformY(y), transformZ(z), color, u, v, overlay, light, nx, ny, nz)
        }

        private fun transformX(value: Float): Float = transform(value, pos.x, cameraX)

        private fun transformY(value: Float): Float = transform(value, pos.y, cameraY)

        private fun transformZ(value: Float): Float = transform(value, pos.z, cameraZ)

        private fun transform(value: Float, blockCoord: Int, cameraCoord: Double): Float {
            if (scale == 1.0f) {
                return (value - cameraCoord).toFloat()
            }
            val local = value - blockCoord
            return (blockCoord - cameraCoord + 0.5 + (local - 0.5) * scale).toFloat()
        }

        companion object {
            private val lineWidthMethod: java.lang.reflect.Method? = runCatching {
                VertexConsumer::class.java.getMethod("lineWidth", Float::class.javaPrimitiveType)
            }.getOrNull()
        }
    }
}

data class PreviewBlockInfo(
    val pos: BlockPos,
    val state: BlockState,
)

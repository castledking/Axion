package axion.client.render

import axion.common.model.ClipboardBuffer
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayers
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

object GhostBlockPreviewRenderer {
    private const val GHOST_ALPHA: Int = 44
    private const val MAX_GHOST_BLOCKS: Int = 1536
    private const val DEFAULT_GHOST_COLOR: Int = 0xFFFFFFFF.toInt()

    fun render(
        context: WorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int = DEFAULT_GHOST_COLOR,
        alpha: Int = GHOST_ALPHA,
    ) {
        if (origins.isEmpty()) {
            return
        }

        val occupiedCells = clipboard.nonAirCells()
        if (occupiedCells.isEmpty() || occupiedCells.size * origins.size > MAX_GHOST_BLOCKS) {
            return
        }

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers() ?: return
        val consumer = consumers.getBuffer(RenderLayers.debugFilledBox())
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val shapeContext = ShapeContext.absent()

        origins.forEach { origin ->
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
}

package axion.client.render

import axion.client.selection.SelectionBounds
import axion.client.tool.ExtrudeToolController
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShapes

object ExtrudePreviewRenderer {
    private const val SOURCE_COLOR: Int = 0xFFFF7A7A.toInt()
    private const val DESTINATION_COLOR: Int = 0xFF6ED6A3.toInt()
    private const val LINE_WIDTH: Float = 1.5f
    private const val MAX_BLOCK_OUTLINES: Int = 192

    fun render(context: AxionWorldRenderContext) {
        val preview = ExtrudeToolController.currentPreview() ?: return
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val consumers = context.consumers()
        val consumer = consumers.getBuffer(RenderLayerCompat.lines())
        val matrixStack = context.matrices()
        val sourcePositions = preview.footprint
        val destinationPositions = preview.extrudePositions

        renderPositions(
            positions = sourcePositions,
            color = SOURCE_COLOR,
            matrixStack = matrixStack,
            consumer = consumer,
            cameraPosX = -cameraPos.x,
            cameraPosY = -cameraPos.y,
            cameraPosZ = -cameraPos.z,
        )

        renderPositions(
            positions = destinationPositions,
            color = DESTINATION_COLOR,
            matrixStack = matrixStack,
            consumer = consumer,
            cameraPosX = -cameraPos.x,
            cameraPosY = -cameraPos.y,
            cameraPosZ = -cameraPos.z,
        )
    }

    private fun renderPositions(
        positions: List<BlockPos>,
        color: Int,
        matrixStack: net.minecraft.client.util.math.MatrixStack,
        consumer: net.minecraft.client.render.VertexConsumer,
        cameraPosX: Double,
        cameraPosY: Double,
        cameraPosZ: Double,
    ) {
        if (positions.isEmpty()) {
            return
        }

        if (positions.size <= MAX_BLOCK_OUTLINES) {
            positions.forEach { pos ->
                VertexRenderingCompat.drawOutline(
                    matrixStack,
                    consumer,
                    VoxelShapes.cuboid(SelectionBounds.outlineBox(SelectionBounds.blockBox(pos))),
                    cameraPosX,
                    cameraPosY,
                    cameraPosZ,
                    color,
                    LINE_WIDTH,
                )
            }
            return
        }

        VertexRenderingCompat.drawOutline(
            matrixStack,
            consumer,
            VoxelShapes.cuboid(SelectionBounds.outlineBox(boundsBox(positions))),
            cameraPosX,
            cameraPosY,
            cameraPosZ,
            color,
            LINE_WIDTH,
        )
    }

    private fun boundsBox(positions: List<BlockPos>): Box {
        val minX = positions.minOf { it.x }.toDouble()
        val minY = positions.minOf { it.y }.toDouble()
        val minZ = positions.minOf { it.z }.toDouble()
        val maxX = positions.maxOf { it.x } + 1.0
        val maxY = positions.maxOf { it.y } + 1.0
        val maxZ = positions.maxOf { it.z } + 1.0
        return Box(minX, minY, minZ, maxX, maxY, maxZ)
    }
}

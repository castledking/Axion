package axion.client.render

import axion.client.AxionClientState
import axion.client.selection.SelectionBounds
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.VertexRendering
import net.minecraft.util.shape.VoxelShapes

object SymmetryPreviewRenderer {
    private const val PRIMARY_COLOR: Int = 0xFFFFE17A.toInt()
    private const val PREVIEW_COLOR: Int = 0xFFBDEFFF.toInt()
    private const val LINE_WIDTH: Float = 1.5f
    private const val MAX_OUTLINES: Int = 64

    fun render(context: WorldRenderContext) {
        val preview = AxionClientState.symmetryPreviewState ?: return
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val consumers = context.consumers() ?: return
        val consumer = consumers.getBuffer(RenderLayers.lines())
        val matrixStack = context.matrices()

        VertexRendering.drawOutline(
            matrixStack,
            consumer,
            VoxelShapes.cuboid(SelectionBounds.outlineBox(SelectionBounds.blockBox(preview.sourceBlock))),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            PRIMARY_COLOR,
            LINE_WIDTH,
        )

        preview.transformedBlocks.take(MAX_OUTLINES).forEach { pos ->
            VertexRendering.drawOutline(
                matrixStack,
                consumer,
                VoxelShapes.cuboid(SelectionBounds.outlineBox(SelectionBounds.blockBox(pos))),
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z,
                PREVIEW_COLOR,
                LINE_WIDTH,
            )
        }
    }
}

package axion.client.render

import axion.client.selection.AxionTarget
import axion.client.selection.SelectionBounds
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
import axion.client.tool.AxionToolSelectionController
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.VertexRendering
import net.minecraft.util.shape.VoxelShapes

object TargetHighlightRenderer {
    private const val TARGET_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val FACE_TARGET_COLOR: Int = 0xFFFFF29A.toInt()
    private const val LINE_WIDTH: Float = 1.5f
    private const val FACE_LINE_WIDTH: Float = 2.25f

    fun render(context: WorldRenderContext) {
        if (!AxionToolSelectionController.isAxionSlotActive()) {
            return
        }

        val target = SelectionController.currentTarget()
        if (target == AxionTarget.MissTarget) {
            return
        }

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val consumers = context.consumers() ?: return
        val consumer = consumers.getBuffer(RenderLayers.lines())
        val matrixStack = context.matrices()
        val blockPos = target.blockPosOrNull() ?: return
        val box = SelectionBounds.outlineBox(SelectionBounds.blockBox(blockPos))
        val color = if (target is AxionTarget.FaceTarget) FACE_TARGET_COLOR else TARGET_COLOR

        VertexRendering.drawOutline(
            matrixStack,
            consumer,
            VoxelShapes.cuboid(box),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            color,
            LINE_WIDTH,
        )

        if (target is AxionTarget.FaceTarget) {
            VertexRendering.drawOutline(
                matrixStack,
                consumer,
                VoxelShapes.cuboid(SelectionBounds.outlineBox(SelectionBounds.faceBox(blockPos, target.face))),
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z,
                FACE_TARGET_COLOR,
                FACE_LINE_WIDTH,
            )
        }
    }
}

package axion.client.render

import axion.client.AxionClientState
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
    private const val TARGET_COLOR: Int = 0xFF000000.toInt()
    private const val LINE_WIDTH: Float = 1.0f

    fun render(context: WorldRenderContext) {
        val blockPos = currentTargetForRender() ?: return
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val consumers = context.consumers() ?: return
        val consumer = consumers.getBuffer(RenderLayers.lines())
        val matrixStack = context.matrices()
        val box = SelectionBounds.outlineBox(SelectionBounds.blockBox(blockPos))

        VertexRendering.drawOutline(
            matrixStack,
            consumer,
            VoxelShapes.cuboid(box),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            TARGET_COLOR,
            LINE_WIDTH,
        )
    }

    private fun currentTargetForRender(): net.minecraft.util.math.BlockPos? {
        val toolActive = AxionToolSelectionController.isAxionSlotActive()
        val modeActive = AxionClientState.globalModeState.infiniteReachEnabled
        if (!toolActive && !modeActive) {
            return null
        }

        val target = SelectionController.currentTarget()
        if (target == AxionTarget.MissTarget) {
            return null
        }
        return target.blockPosOrNull()?.toImmutable()
    }
}

package axion.client.render

import axion.client.AxionClientState
import axion.client.selection.AxionTarget
import axion.client.selection.SelectionBounds
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
import axion.client.tool.AxionToolSelectionController
import net.minecraft.client.MinecraftClient
import net.minecraft.util.shape.VoxelShapes

object TargetHighlightRenderer {
    private const val TARGET_COLOR: Int = 0xFF000000.toInt()
    private const val LINE_WIDTH: Float = 1.0f

    fun render(context: AxionWorldRenderContext) {
        val blockPos = currentTargetForRender() ?: return
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val consumers = context.consumers()
        val consumer = consumers.getBuffer(RenderLayerCompat.lines())
        val matrixStack = context.matrices()
        val box = SelectionBounds.outlineBox(SelectionBounds.blockBox(blockPos))

        VertexRenderingCompat.drawOutline(
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

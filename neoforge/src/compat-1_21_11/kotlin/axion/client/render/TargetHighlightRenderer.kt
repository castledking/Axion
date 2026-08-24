package axion.client.render
import axion.client.compat.CameraAccess

import axion.client.AxionClientState
import axion.client.current.AxionTarget
import axion.client.current.SelectionBounds
import axion.client.current.SelectionController
import axion.client.current.blockPosOrNull
import axion.client.itemStack.AxionToolSelectionController
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.world.phys.shapes.Shapes

object TargetHighlightRenderer {
    private const val TARGET_COLOR: Int = 0xFF000000.toInt()
    private const val LINE_WIDTH: Float = 1.0f

    fun render(context: AxionWorldRenderContext) {
        val blockPos = currentTargetForRender() ?: return
        val client = Minecraft.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = CameraAccess.getPos(camera)
        val consumers = context.consumers()
        val renderLayer = try {
            RenderLayerCompat.lines()
        } catch (e: Exception) {
            // Silently skip rendering if RenderLayerCompat fails
            return
        }
        val consumer = consumers.getBuffer(renderLayer)
        val matrixStack = context.matrices()
        val box = SelectionBounds.outlineBox(SelectionBounds.blockBox(blockPos))

        VertexRenderingCompat.drawOutline(
            matrixStack,
            consumer,
            Shapes.create(box),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            TARGET_COLOR,
            LINE_WIDTH,
        )
    }

    private fun currentTargetForRender(): net.minecraft.core.BlockPos? {
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

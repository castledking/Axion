package axion.client.render

import axion.client.AxionClientState
import axion.common.model.SymmetryState
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShapes

object SymmetryGizmoRenderer {
    private const val GIZMO_COLOR: Int = 0xFFF2C94C.toInt()
    private const val HALF_SIZE: Double = 2.0 / 16.0
    private const val LINE_WIDTH: Float = 1.5f

    fun render(context: AxionWorldRenderContext) {
        val state = AxionClientState.symmetryState
        val config = when (state) {
            SymmetryState.Inactive -> return
            is SymmetryState.Active -> state.config
        }

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val consumers = context.consumers()
        val matrixStack = context.matrices()
        val box = gizmoBox(config.anchor.position)
        VertexRenderingCompat.drawOutline(
            matrixStack,
            consumers.getBuffer(RenderLayerCompat.lines()),
            VoxelShapes.cuboid(box),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            GIZMO_COLOR,
            LINE_WIDTH,
        )
    }

    private fun gizmoBox(anchor: net.minecraft.util.math.Vec3d): Box {
        return Box(
            anchor.x - HALF_SIZE,
            anchor.y - HALF_SIZE,
            anchor.z - HALF_SIZE,
            anchor.x + HALF_SIZE,
            anchor.y + HALF_SIZE,
            anchor.z + HALF_SIZE,
        )
    }
}

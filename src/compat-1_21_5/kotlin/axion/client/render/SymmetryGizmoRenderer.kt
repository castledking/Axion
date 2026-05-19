package axion.client.render
import axion.client.compat.CameraAccess

import axion.client.AxionClientState
import axion.common.model.SymmetryState
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShapes

object SymmetryGizmoRenderer {
    private const val GIZMO_COLOR: Int = 0xFFF2C94C.toInt()
    private const val HALF_SIZE: Double = 2.0 / 16.0
    private const val LINE_WIDTH: Float = 1.5f
    private const val FILL_ALPHA: Int = 130

    fun render(context: AxionWorldRenderContext) {
        val state = AxionClientState.symmetryState
        val config = when (state) {
            SymmetryState.Inactive -> return
            is SymmetryState.Active -> state.config
        }

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = CameraAccess.getPos(camera)
        val consumers = context.consumers()
        val matrixStack = context.matrices()
        val box = gizmoBox(config.anchor.position)

        val fillLayer = RenderLayerCompat.xrayQuads()
        val lineLayer = RenderLayerCompat.lines()
        DepthRenderCompat.renderThroughBlocks(consumers, fillLayer, lineLayer) {
            PulsingCuboidRenderer.renderFilledBox(
                matrixStack = matrixStack,
                consumer = consumers.getBuffer(fillLayer),
                layer = fillLayer,
                cameraPos = cameraPos,
                box = box,
                alpha = FILL_ALPHA,
                color = GIZMO_COLOR,
            )

            VertexRenderingCompat.drawOutline(
                matrixStack,
                consumers.getBuffer(lineLayer),
                VoxelShapes.cuboid(box),
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z,
                GIZMO_COLOR,
                LINE_WIDTH,
            )
        }
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

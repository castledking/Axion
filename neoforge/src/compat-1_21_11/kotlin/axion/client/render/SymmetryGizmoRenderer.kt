package axion.client.render
import axion.client.compat.CameraAccess

import axion.client.AxionClientState
import axion.common.model.SymmetryState
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes

object SymmetryGizmoRenderer {
    private const val HALF_SIZE: Double = 2.0 / 16.0
    private const val LINE_WIDTH: Float = 1.5f
    private const val FILL_ALPHA: Int = 130

    fun render(context: AxionWorldRenderContext) {
        val state = AxionClientState.symmetryState
        val config = when (state) {
            SymmetryState.Inactive -> return
            is SymmetryState.Active -> state.config
        }

        val client = Minecraft.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = CameraAccess.getPos(camera)
        val consumers = context.consumers()
        val matrixStack = context.matrices()
        val box = gizmoBox(config.anchor.position)
        val color = SymmetryGizmoStylePolicy.color(
            rotationalEnabled = config.rotationalEnabled,
            mirrorEnabled = config.mirrorEnabled,
        )

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
                color = color,
            )

            VertexRenderingCompat.drawOutline(
                matrixStack,
                consumers.getBuffer(lineLayer),
                Shapes.create(box),
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z,
                color,
                LINE_WIDTH,
            )
        }
    }

    private fun gizmoBox(anchor: net.minecraft.world.phys.Vec3): AABB {
        return AABB(
            anchor.x - HALF_SIZE,
            anchor.y - HALF_SIZE,
            anchor.z - HALF_SIZE,
            anchor.x + HALF_SIZE,
            anchor.y + HALF_SIZE,
            anchor.z + HALF_SIZE,
        )
    }
}

package axion.client.render

import axion.client.AxionClientState
import axion.common.model.SymmetryState
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayers
import net.minecraft.util.math.Box

object SymmetryGizmoRenderer {
    private const val GIZMO_COLOR: Int = 0xFFF2C94C.toInt()
    private const val GIZMO_ALPHA: Int = 138
    private const val HALF_SIZE: Double = 2.0 / 16.0

    fun render(context: WorldRenderContext) {
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

        PulsingCuboidRenderer.renderFilledBox(
            matrixStack = matrixStack,
            consumer = consumers.getBuffer(RenderLayers.lightning()),
            cameraPos = cameraPos,
            box = box,
            alpha = GIZMO_ALPHA,
            color = GIZMO_COLOR,
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

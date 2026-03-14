package axion.client.render

import axion.client.AxionClientState
import axion.common.model.SymmetryState
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.VertexRendering
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShapes

object SymmetryGizmoRenderer {
    private const val GIZMO_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val LINE_WIDTH: Float = 2.0f
    private const val HALF_SIZE: Double = 2.0

    fun render(context: WorldRenderContext) {
        val state = AxionClientState.symmetryState
        val config = when (state) {
            SymmetryState.Inactive -> return
            is SymmetryState.Active -> state.config
        }

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val cameraPos = camera.cameraPos
        val consumers = context.consumers() ?: return
        val consumer = consumers.getBuffer(RenderLayers.lines())
        val matrixStack = context.matrices()
        val anchor = config.anchor.position
        val box = Box(
            anchor.x - HALF_SIZE,
            anchor.y - HALF_SIZE,
            anchor.z - HALF_SIZE,
            anchor.x + HALF_SIZE,
            anchor.y + HALF_SIZE,
            anchor.z + HALF_SIZE,
        )

        VertexRendering.drawOutline(
            matrixStack,
            consumer,
            VoxelShapes.cuboid(box),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            GIZMO_COLOR,
            LINE_WIDTH,
        )
    }
}

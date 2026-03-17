package axion.client.render

import axion.client.AxionClientState
import axion.common.model.SymmetryState
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.VertexRendering
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShapes

object SymmetryGizmoRenderer {
    private const val GIZMO_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val LINE_WIDTH: Float = 2.0f
    private const val HALF_THICKNESS: Double = 2.0 / 16.0
    private const val NIB_DEPTH: Double = 0.5

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
        val consumer = consumers.getBuffer(RenderLayers.lines())
        val matrixStack = context.matrices()
        val box = gizmoBox(config.anchor.position, config.anchor.face)

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

    private fun gizmoBox(anchor: net.minecraft.util.math.Vec3d, face: Direction?): Box {
        if (face == null) {
            return Box(
                anchor.x - HALF_THICKNESS,
                anchor.y - HALF_THICKNESS,
                anchor.z - HALF_THICKNESS,
                anchor.x + HALF_THICKNESS,
                anchor.y + HALF_THICKNESS,
                anchor.z + HALF_THICKNESS,
            )
        }

        return when (face.axis) {
            Direction.Axis.X -> Box(
                if (face.offsetX < 0) anchor.x - NIB_DEPTH else anchor.x,
                anchor.y - HALF_THICKNESS,
                anchor.z - HALF_THICKNESS,
                if (face.offsetX < 0) anchor.x else anchor.x + NIB_DEPTH,
                anchor.y + HALF_THICKNESS,
                anchor.z + HALF_THICKNESS,
            )

            Direction.Axis.Y -> Box(
                anchor.x - HALF_THICKNESS,
                if (face.offsetY < 0) anchor.y - NIB_DEPTH else anchor.y,
                anchor.z - HALF_THICKNESS,
                anchor.x + HALF_THICKNESS,
                if (face.offsetY < 0) anchor.y else anchor.y + NIB_DEPTH,
                anchor.z + HALF_THICKNESS,
            )

            Direction.Axis.Z -> Box(
                anchor.x - HALF_THICKNESS,
                anchor.y - HALF_THICKNESS,
                if (face.offsetZ < 0) anchor.z - NIB_DEPTH else anchor.z,
                anchor.x + HALF_THICKNESS,
                anchor.y + HALF_THICKNESS,
                if (face.offsetZ < 0) anchor.z else anchor.z + NIB_DEPTH,
            )
        }
    }
}

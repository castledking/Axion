package axion.client.render

import axion.client.AxionClientState
import axion.client.selection.SelectionBounds
import axion.client.tool.AxionToolSelectionController
import axion.common.model.AxionSubtool
import axion.common.model.SelectionState
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.VertexRendering
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShapes

object SelectionBoxRenderer {
    private const val REGION_COLOR: Int = 0xFFFFFFFF.toInt()
    private const val ANCHOR_COLOR: Int = 0xFFFF5A5A.toInt()
    private const val SECOND_CORNER_COLOR: Int = 0xFF5AA8FF.toInt()
    private const val LINE_WIDTH: Float = 2.0f
    private const val CORNER_LINE_WIDTH: Float = 2.5f
    private const val CORNER_MARKER_INSET: Double = 0.32
    private const val SELECTION_PULSE_MIN_ALPHA: Int = 0
    private const val SELECTION_PULSE_MAX_ALPHA: Int = 166

    fun render(context: WorldRenderContext) {
        if (!shouldRenderSelectionPulse()) {
            return
        }

        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers() ?: return
        val consumer = consumers.getBuffer(RenderLayers.lines())
        val matrixStack = context.matrices()
        val cameraPos = camera.cameraPos
        val state = AxionClientState.selectionState

        when (state) {
            SelectionState.Idle -> return

            is SelectionState.FirstCornerSet -> {
                PulsingCuboidRenderer.renderShell(
                    context = context,
                    box = SelectionBounds.blockBox(state.firstCorner),
                    outlineColor = ANCHOR_COLOR,
                    lineWidth = CORNER_LINE_WIDTH,
                    minAlpha = SELECTION_PULSE_MIN_ALPHA,
                    maxAlpha = SELECTION_PULSE_MAX_ALPHA,
                )
                drawCornerMarker(
                    matrixStack = matrixStack,
                    consumer = consumer,
                    cameraPos = cameraPos,
                    pos = state.firstCorner,
                    color = ANCHOR_COLOR,
                )
            }

            is SelectionState.RegionDefined -> {
                PulsingCuboidRenderer.renderShell(
                    context = context,
                    box = SelectionBounds.regionBox(state.region()),
                    outlineColor = REGION_COLOR,
                    lineWidth = LINE_WIDTH,
                    minAlpha = SELECTION_PULSE_MIN_ALPHA,
                    maxAlpha = SELECTION_PULSE_MAX_ALPHA,
                )
                drawCornerMarker(matrixStack, consumer, cameraPos, state.firstCorner, ANCHOR_COLOR)
                drawCornerMarker(matrixStack, consumer, cameraPos, state.secondCorner, SECOND_CORNER_COLOR)
            }
        }
    }

    private fun drawCornerMarker(
        matrixStack: net.minecraft.client.util.math.MatrixStack,
        consumer: net.minecraft.client.render.VertexConsumer,
        cameraPos: net.minecraft.util.math.Vec3d,
        pos: BlockPos,
        color: Int,
    ) {
        drawOutline(
            matrixStack = matrixStack,
            consumer = consumer,
            cameraPos = cameraPos,
            box = SelectionBounds.outlineBox(SelectionBounds.blockBox(pos)),
            color = color,
            lineWidth = CORNER_LINE_WIDTH,
        )
        drawOutline(
            matrixStack = matrixStack,
            consumer = consumer,
            cameraPos = cameraPos,
            box = cornerMarkerBox(pos),
            color = color,
            lineWidth = CORNER_LINE_WIDTH,
        )
    }

    private fun cornerMarkerBox(pos: BlockPos): Box {
        return Box(
            pos.x + CORNER_MARKER_INSET,
            pos.y + CORNER_MARKER_INSET,
            pos.z + CORNER_MARKER_INSET,
            pos.x + 1.0 - CORNER_MARKER_INSET,
            pos.y + 1.0 - CORNER_MARKER_INSET,
            pos.z + 1.0 - CORNER_MARKER_INSET,
        )
    }

    private fun shouldRenderSelectionPulse(): Boolean {
        if (!AxionToolSelectionController.isAxionSelected()) {
            return false
        }

        return when (AxionClientState.selectedSubtool) {
            AxionSubtool.MOVE,
            AxionSubtool.CLONE,
            AxionSubtool.STACK,
            AxionSubtool.SMEAR,
            AxionSubtool.ERASE,
                -> true

            AxionSubtool.SETUP_SYMMETRY,
            AxionSubtool.EXTRUDE,
                -> false
        }
    }

    private fun drawOutline(
        matrixStack: net.minecraft.client.util.math.MatrixStack,
        consumer: net.minecraft.client.render.VertexConsumer,
        cameraPos: net.minecraft.util.math.Vec3d,
        box: Box,
        color: Int,
        lineWidth: Float,
    ) {
        VertexRendering.drawOutline(
            matrixStack,
            consumer,
            VoxelShapes.cuboid(box),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            color,
            lineWidth,
        )
    }
}

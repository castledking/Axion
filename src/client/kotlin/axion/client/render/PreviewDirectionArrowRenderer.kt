package axion.client.render

import axion.client.selection.SelectionBounds
import axion.common.model.BlockRegion
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.math.abs

object PreviewDirectionArrowRenderer {
    private const val ARROW_OFFSET: Double = 0.32
    private const val SHAFT_LENGTH: Double = 0.68
    private const val HEAD_LENGTH: Double = 0.34
    private const val SHAFT_RADIUS: Double = 0.05
    private const val HEAD_BASE_RADIUS: Double = 0.14
    private const val HEAD_TIP_RADIUS: Double = 0.025
    private const val ALPHA: Int = 214
    private const val X_COLOR: Int = 0xFFFF4D4D.toInt()
    private const val Y_COLOR: Int = 0xFF50D26D.toInt()
    private const val Z_COLOR: Int = 0xFF5AA8FF.toInt()

    fun render(context: AxionWorldRenderContext, region: BlockRegion) {
        val axisDirection = liveLookDirection() ?: return
        render(context, region, axisDirection.vector)
    }

    fun render(context: AxionWorldRenderContext, region: BlockRegion, direction: Vec3i) {
        val axis = dominantAxis(direction) ?: return
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers()
        val entry = context.matrices().peek()
        val cameraPos = camera.cameraPos
        val consumer = consumers.getBuffer(RenderLayerCompat.lightning())
        val box = SelectionBounds.outlineBox(SelectionBounds.regionBox(region.normalized()))

        val arrow = when (axis) {
            Axis.X -> arrowAlongX(box, direction.x >= 0)
            Axis.Y -> arrowAlongY(box, direction.y >= 0)
            Axis.Z -> arrowAlongZ(box, direction.z >= 0)
        }
        renderArrowGeometry(
            consumer = consumer,
            entry = entry,
            cameraPos = cameraPos,
            arrow = arrow,
        )
    }

    private fun liveLookDirection(): Direction? {
        val look = MinecraftClient.getInstance().player?.rotationVecClient ?: return null
        return Direction.getFacing(look)
    }

    private fun arrowAlongX(box: Box, positive: Boolean): ArrowData {
        val y = (box.minY + box.maxY) * 0.5
        val z = (box.minZ + box.maxZ) * 0.5
        val startX = if (positive) box.maxX + ARROW_OFFSET else box.minX - ARROW_OFFSET
        return ArrowData(
            start = Vec3d(startX, y, z),
            axis = Axis.X,
            positive = positive,
            color = X_COLOR,
        )
    }

    private fun arrowAlongY(box: Box, positive: Boolean): ArrowData {
        val x = (box.minX + box.maxX) * 0.5
        val z = (box.minZ + box.maxZ) * 0.5
        val startY = if (positive) box.maxY + ARROW_OFFSET else box.minY - ARROW_OFFSET
        return ArrowData(
            start = Vec3d(x, startY, z),
            axis = Axis.Y,
            positive = positive,
            color = Y_COLOR,
        )
    }

    private fun arrowAlongZ(box: Box, positive: Boolean): ArrowData {
        val x = (box.minX + box.maxX) * 0.5
        val y = (box.minY + box.maxY) * 0.5
        val startZ = if (positive) box.maxZ + ARROW_OFFSET else box.minZ - ARROW_OFFSET
        return ArrowData(
            start = Vec3d(x, y, startZ),
            axis = Axis.Z,
            positive = positive,
            color = Z_COLOR,
        )
    }

    private fun renderArrowGeometry(
        consumer: VertexConsumer,
        entry: net.minecraft.client.util.math.MatrixStack.Entry,
        cameraPos: Vec3d,
        arrow: ArrowData,
    ) {
        val basis = axisBasis(arrow.axis, arrow.positive)
        val shaftEnd = arrow.start.add(basis.forward.multiply(SHAFT_LENGTH))
        val tipCenter = shaftEnd.add(basis.forward.multiply(HEAD_LENGTH))

        emitPrism(
            consumer = consumer,
            entry = entry,
            cameraPos = cameraPos,
            start = arrow.start,
            end = shaftEnd,
            right = basis.right,
            up = basis.up,
            radius = SHAFT_RADIUS,
            color = arrow.color,
        )
        emitFrustum(
            consumer = consumer,
            entry = entry,
            cameraPos = cameraPos,
            start = shaftEnd,
            end = tipCenter,
            right = basis.right,
            up = basis.up,
            startRadius = HEAD_BASE_RADIUS,
            endRadius = HEAD_TIP_RADIUS,
            color = arrow.color,
        )
    }

    private fun emitPrism(
        consumer: VertexConsumer,
        entry: net.minecraft.client.util.math.MatrixStack.Entry,
        cameraPos: Vec3d,
        start: Vec3d,
        end: Vec3d,
        right: Vec3d,
        up: Vec3d,
        radius: Double,
        color: Int,
    ) {
        val cornersStart = squareCorners(start, right, up, radius)
        val cornersEnd = squareCorners(end, right, up, radius)
        emitQuad(consumer, entry, cameraPos, cornersStart[0], cornersStart[1], cornersEnd[1], cornersEnd[0], color)
        emitQuad(consumer, entry, cameraPos, cornersStart[1], cornersStart[2], cornersEnd[2], cornersEnd[1], color)
        emitQuad(consumer, entry, cameraPos, cornersStart[2], cornersStart[3], cornersEnd[3], cornersEnd[2], color)
        emitQuad(consumer, entry, cameraPos, cornersStart[3], cornersStart[0], cornersEnd[0], cornersEnd[3], color)
        emitQuad(consumer, entry, cameraPos, cornersStart[0], cornersStart[3], cornersStart[2], cornersStart[1], color)
    }

    private fun emitFrustum(
        consumer: VertexConsumer,
        entry: net.minecraft.client.util.math.MatrixStack.Entry,
        cameraPos: Vec3d,
        start: Vec3d,
        end: Vec3d,
        right: Vec3d,
        up: Vec3d,
        startRadius: Double,
        endRadius: Double,
        color: Int,
    ) {
        val base = squareCorners(start, right, up, startRadius)
        val tip = squareCorners(end, right, up, endRadius)
        emitQuad(consumer, entry, cameraPos, base[0], base[1], tip[1], tip[0], color)
        emitQuad(consumer, entry, cameraPos, base[1], base[2], tip[2], tip[1], color)
        emitQuad(consumer, entry, cameraPos, base[2], base[3], tip[3], tip[2], color)
        emitQuad(consumer, entry, cameraPos, base[3], base[0], tip[0], tip[3], color)
        emitQuad(consumer, entry, cameraPos, tip[0], tip[1], tip[2], tip[3], color)
    }

    private fun squareCorners(center: Vec3d, right: Vec3d, up: Vec3d, radius: Double): Array<Vec3d> {
        val rightOffset = right.multiply(radius)
        val upOffset = up.multiply(radius)
        return arrayOf(
            center.add(rightOffset).add(upOffset),
            center.subtract(rightOffset).add(upOffset),
            center.subtract(rightOffset).subtract(upOffset),
            center.add(rightOffset).subtract(upOffset),
        )
    }

    private fun emitQuad(
        consumer: VertexConsumer,
        entry: net.minecraft.client.util.math.MatrixStack.Entry,
        cameraPos: Vec3d,
        a: Vec3d,
        b: Vec3d,
        c: Vec3d,
        d: Vec3d,
        color: Int,
    ) {
        emitVertex(consumer, entry, cameraPos, a, color)
        emitVertex(consumer, entry, cameraPos, b, color)
        emitVertex(consumer, entry, cameraPos, c, color)
        emitVertex(consumer, entry, cameraPos, d, color)
    }

    private fun emitVertex(
        consumer: VertexConsumer,
        entry: net.minecraft.client.util.math.MatrixStack.Entry,
        cameraPos: Vec3d,
        point: Vec3d,
        color: Int,
    ) {
        val red = ((color ushr 16) and 0xFF)
        val green = ((color ushr 8) and 0xFF)
        val blue = (color and 0xFF)
        consumer.vertex(
            entry,
            (point.x - cameraPos.x).toFloat(),
            (point.y - cameraPos.y).toFloat(),
            (point.z - cameraPos.z).toFloat(),
        ).color(red, green, blue, ALPHA)
    }

    private fun axisBasis(axis: Axis, positive: Boolean): AxisBasis {
        val sign = if (positive) 1.0 else -1.0
        return when (axis) {
            Axis.X -> AxisBasis(
                forward = Vec3d(sign, 0.0, 0.0),
                right = Vec3d(0.0, 0.0, 1.0),
                up = Vec3d(0.0, 1.0, 0.0),
            )
            Axis.Y -> AxisBasis(
                forward = Vec3d(0.0, sign, 0.0),
                right = Vec3d(1.0, 0.0, 0.0),
                up = Vec3d(0.0, 0.0, 1.0),
            )
            Axis.Z -> AxisBasis(
                forward = Vec3d(0.0, 0.0, sign),
                right = Vec3d(1.0, 0.0, 0.0),
                up = Vec3d(0.0, 1.0, 0.0),
            )
        }
    }

    private fun dominantAxis(direction: Vec3i): Axis? {
        val ax = abs(direction.x)
        val ay = abs(direction.y)
        val az = abs(direction.z)
        return when (maxOf(ax, ay, az)) {
            0 -> null
            ax -> Axis.X
            ay -> Axis.Y
            else -> Axis.Z
        }
    }

    private data class ArrowData(
        val start: Vec3d,
        val axis: Axis,
        val positive: Boolean,
        val color: Int,
    )

    private data class AxisBasis(
        val forward: Vec3d,
        val right: Vec3d,
        val up: Vec3d,
    )

    private enum class Axis {
        X,
        Y,
        Z,
    }
}

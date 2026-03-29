package axion.client.render

import axion.client.selection.SelectionBounds
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.sin

object PulsingCuboidRenderer {
    private const val DEFAULT_MIN_ALPHA: Int = 52
    private const val DEFAULT_MAX_ALPHA: Int = 76
    private const val PULSE_PERIOD_MILLIS: Double = 2200.0
    private const val SELECTION_PULSE_PERIOD_MILLIS: Double = 3200.0

    fun render(
        context: AxionWorldRenderContext,
        box: Box,
        outlineColor: Int,
        lineWidth: Float,
        minAlpha: Int = DEFAULT_MIN_ALPHA,
        maxAlpha: Int = DEFAULT_MAX_ALPHA,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers()
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val fillLayer = RenderLayerCompat.debugQuads()

        renderFilledBox(
            matrixStack = matrixStack,
            consumer = consumers.getBuffer(fillLayer),
            layer = fillLayer,
            cameraPos = cameraPos,
            box = box,
            alpha = pulsingAlpha(minAlpha, maxAlpha),
        )
        VertexRenderingCompat.drawOutline(
            matrixStack,
            consumers.getBuffer(RenderLayerCompat.lines()),
            VoxelShapes.cuboid(box),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            outlineColor,
            lineWidth,
        )
    }

    fun renderShell(
        context: AxionWorldRenderContext,
        box: Box,
        outlineColor: Int,
        lineWidth: Float,
        minAlpha: Int = DEFAULT_MIN_ALPHA,
        maxAlpha: Int = DEFAULT_MAX_ALPHA,
        fillColor: Int = 0xFFFFFFFF.toInt(),
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers()
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val alpha = pulsingAlpha(minAlpha, maxAlpha)
        val fillLayer = RenderLayerCompat.lightning()
        val consumer = consumers.getBuffer(fillLayer)

        renderFilledBox(
            matrixStack = matrixStack,
            consumer = consumer,
            layer = fillLayer,
            cameraPos = cameraPos,
            box = SelectionBounds.outlineBox(box),
            alpha = alpha,
            color = fillColor,
        )

        VertexRenderingCompat.drawOutline(
            matrixStack,
            consumers.getBuffer(RenderLayerCompat.lines()),
            VoxelShapes.cuboid(SelectionBounds.outlineBox(box)),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            outlineColor,
            lineWidth,
        )
    }

    fun renderSelectionBox(
        context: AxionWorldRenderContext,
        box: Box,
        outlineColor: Int,
        lineWidth: Float,
        baseFillColor: Int,
        baseAlpha: Int,
        pulseFillColor: Int,
        pulseMinAlpha: Int,
        pulseMaxAlpha: Int,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers()
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val outlinedBox = SelectionBounds.outlineBox(box)
        val fillLayer = RenderLayerCompat.debugQuads()
        val filledConsumer = consumers.getBuffer(fillLayer)

        renderFilledBox(
            matrixStack = matrixStack,
            consumer = filledConsumer,
            layer = fillLayer,
            cameraPos = cameraPos,
            box = outlinedBox,
            alpha = baseAlpha,
            color = baseFillColor,
        )
        renderFilledBox(
            matrixStack = matrixStack,
            consumer = filledConsumer,
            layer = fillLayer,
            cameraPos = cameraPos,
            box = outlinedBox,
            alpha = pulsingAlpha(
                minAlpha = pulseMinAlpha,
                maxAlpha = pulseMaxAlpha,
                periodMillis = SELECTION_PULSE_PERIOD_MILLIS,
            ),
            color = pulseFillColor,
        )
        VertexRenderingCompat.drawOutline(
            matrixStack,
            consumers.getBuffer(RenderLayerCompat.lines()),
            VoxelShapes.cuboid(outlinedBox),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            outlineColor,
            lineWidth,
        )
    }

    fun renderOutlineBox(
        context: AxionWorldRenderContext,
        box: Box,
        outlineColor: Int,
        lineWidth: Float,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers()
        val cameraPos = camera.cameraPos

        VertexRenderingCompat.drawOutline(
            context.matrices(),
            consumers.getBuffer(RenderLayerCompat.lines()),
            VoxelShapes.cuboid(SelectionBounds.outlineBox(box)),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            outlineColor,
            lineWidth,
        )
    }

    fun renderFilledBox(
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        layer: RenderLayer,
        cameraPos: Vec3d,
        box: Box,
        alpha: Int,
        color: Int = 0xFFFFFFFF.toInt(),
    ) {
        val minX = (box.minX - cameraPos.x).toFloat()
        val minY = (box.minY - cameraPos.y).toFloat()
        val minZ = (box.minZ - cameraPos.z).toFloat()
        val maxX = (box.maxX - cameraPos.x).toFloat()
        val maxY = (box.maxY - cameraPos.y).toFloat()
        val maxZ = (box.maxZ - cameraPos.z).toFloat()
        val entry = matrixStack.peek()
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF

        val drawMode = layer.drawMode

        emitFace(consumer, drawMode, entry, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0f, 0f, -1f, red, green, blue, alpha)
        emitFace(consumer, drawMode, entry, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, 0f, 0f, 1f, red, green, blue, alpha)
        emitFace(consumer, drawMode, entry, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, -1f, 0f, 0f, red, green, blue, alpha)
        emitFace(consumer, drawMode, entry, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 1f, 0f, 0f, red, green, blue, alpha)
        emitFace(consumer, drawMode, entry, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0f, 1f, 0f, red, green, blue, alpha)
        emitFace(consumer, drawMode, entry, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 0f, -1f, 0f, red, green, blue, alpha)
    }

    private fun emitFace(
        consumer: VertexConsumer,
        drawMode: VertexFormat.DrawMode,
        entry: MatrixStack.Entry,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        x4: Float,
        y4: Float,
        z4: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        when (drawMode) {
            VertexFormat.DrawMode.QUADS -> emitQuad(
                consumer, entry, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4,
                normalX, normalY, normalZ, red, green, blue, alpha,
            )

            VertexFormat.DrawMode.TRIANGLES -> emitTriangles(
                consumer, entry, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4,
                normalX, normalY, normalZ, red, green, blue, alpha,
            )

            else -> emitTriangles(
                consumer, entry, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4,
                normalX, normalY, normalZ, red, green, blue, alpha,
            )
        }
    }

    private fun emitQuad(
        consumer: VertexConsumer,
        entry: MatrixStack.Entry,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        x4: Float,
        y4: Float,
        z4: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        consumer.vertex(entry, x1, y1, z1).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x2, y2, z2).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x3, y3, z3).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x4, y4, z4).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x4, y4, z4).color(red, green, blue, alpha).normal(entry, -normalX, -normalY, -normalZ)
        consumer.vertex(entry, x3, y3, z3).color(red, green, blue, alpha).normal(entry, -normalX, -normalY, -normalZ)
        consumer.vertex(entry, x2, y2, z2).color(red, green, blue, alpha).normal(entry, -normalX, -normalY, -normalZ)
        consumer.vertex(entry, x1, y1, z1).color(red, green, blue, alpha).normal(entry, -normalX, -normalY, -normalZ)
    }

    private fun emitTriangles(
        consumer: VertexConsumer,
        entry: MatrixStack.Entry,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        x4: Float,
        y4: Float,
        z4: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        emitTriangle(consumer, entry, x1, y1, z1, x2, y2, z2, x3, y3, z3, normalX, normalY, normalZ, red, green, blue, alpha)
        emitTriangle(consumer, entry, x1, y1, z1, x3, y3, z3, x4, y4, z4, normalX, normalY, normalZ, red, green, blue, alpha)
        emitTriangle(consumer, entry, x4, y4, z4, x3, y3, z3, x2, y2, z2, -normalX, -normalY, -normalZ, red, green, blue, alpha)
        emitTriangle(consumer, entry, x4, y4, z4, x2, y2, z2, x1, y1, z1, -normalX, -normalY, -normalZ, red, green, blue, alpha)
    }

    private fun emitTriangle(
        consumer: VertexConsumer,
        entry: MatrixStack.Entry,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        consumer.vertex(entry, x1, y1, z1).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x2, y2, z2).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x3, y3, z3).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
    }

    fun pulsingAlpha(
        minAlpha: Int,
        maxAlpha: Int,
        periodMillis: Double = PULSE_PERIOD_MILLIS,
    ): Int {
        val phase = (System.currentTimeMillis() % periodMillis) / periodMillis
        val wave = (sin(phase * Math.PI * 2.0) + 1.0) * 0.5
        return (minAlpha + ((maxAlpha - minAlpha) * wave)).toInt()
    }
}

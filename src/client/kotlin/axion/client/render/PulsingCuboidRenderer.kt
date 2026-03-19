package axion.client.render

import axion.client.selection.SelectionBounds
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexRendering
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.sin

object PulsingCuboidRenderer {
    private const val DEFAULT_MIN_ALPHA: Int = 52
    private const val DEFAULT_MAX_ALPHA: Int = 92
    private const val PULSE_PERIOD_MILLIS: Double = 2200.0

    fun render(
        context: WorldRenderContext,
        box: Box,
        outlineColor: Int,
        lineWidth: Float,
        minAlpha: Int = DEFAULT_MIN_ALPHA,
        maxAlpha: Int = DEFAULT_MAX_ALPHA,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers() ?: return
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()

        renderFilledBox(
            matrixStack = matrixStack,
            consumer = consumers.getBuffer(RenderLayers.debugFilledBox()),
            cameraPos = cameraPos,
            box = box,
            alpha = pulsingAlpha(minAlpha, maxAlpha),
        )
        VertexRendering.drawOutline(
            matrixStack,
            consumers.getBuffer(RenderLayers.lines()),
            VoxelShapes.cuboid(box),
            -cameraPos.x,
            -cameraPos.y,
            -cameraPos.z,
            outlineColor,
            lineWidth,
        )
    }

    fun renderShell(
        context: WorldRenderContext,
        box: Box,
        outlineColor: Int,
        lineWidth: Float,
        minAlpha: Int = DEFAULT_MIN_ALPHA,
        maxAlpha: Int = DEFAULT_MAX_ALPHA,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera ?: return
        val consumers = context.consumers() ?: return
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val alpha = pulsingAlpha(minAlpha, maxAlpha)
        val consumer = consumers.getBuffer(RenderLayers.lightning())

        renderFaceQuads(
            matrixStack = matrixStack,
            consumer = consumer,
            cameraPos = cameraPos,
            box = SelectionBounds.outlineBox(box),
            alpha = alpha,
        )

        VertexRendering.drawOutline(
            matrixStack,
            consumers.getBuffer(RenderLayers.lines()),
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

        emitQuad(consumer, entry, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0f, 0f, -1f, red, green, blue, alpha)
        emitQuad(consumer, entry, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, 0f, 0f, 1f, red, green, blue, alpha)
        emitQuad(consumer, entry, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, -1f, 0f, 0f, red, green, blue, alpha)
        emitQuad(consumer, entry, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 1f, 0f, 0f, red, green, blue, alpha)
        emitQuad(consumer, entry, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0f, 1f, 0f, red, green, blue, alpha)
        emitQuad(consumer, entry, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 0f, -1f, 0f, red, green, blue, alpha)
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
    }

    private fun renderFaceQuads(
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
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

        emitQuad(consumer, entry, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0f, 0f, -1f, red, green, blue, alpha)
        emitQuad(consumer, entry, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, 0f, 0f, 1f, red, green, blue, alpha)
        emitQuad(consumer, entry, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, -1f, 0f, 0f, red, green, blue, alpha)
        emitQuad(consumer, entry, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 1f, 0f, 0f, red, green, blue, alpha)
        emitQuad(consumer, entry, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0f, 1f, 0f, red, green, blue, alpha)
        emitQuad(consumer, entry, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 0f, -1f, 0f, red, green, blue, alpha)
    }

    private fun pulsingAlpha(minAlpha: Int, maxAlpha: Int): Int {
        val phase = (System.currentTimeMillis() % PULSE_PERIOD_MILLIS) / PULSE_PERIOD_MILLIS
        val wave = (sin(phase * Math.PI * 2.0) + 1.0) * 0.5
        return (minAlpha + ((maxAlpha - minAlpha) * wave)).toInt()
    }
}

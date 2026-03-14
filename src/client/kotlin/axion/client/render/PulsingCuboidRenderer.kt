package axion.client.render

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
    private const val MIN_ALPHA: Int = 52
    private const val MAX_ALPHA: Int = 92
    private const val PULSE_PERIOD_MILLIS: Double = 900.0

    fun render(
        context: WorldRenderContext,
        box: Box,
        outlineColor: Int,
        lineWidth: Float,
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
            alpha = pulsingAlpha(),
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

    fun renderFilledBox(
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        cameraPos: Vec3d,
        box: Box,
        alpha: Int,
    ) {
        val minX = (box.minX - cameraPos.x).toFloat()
        val minY = (box.minY - cameraPos.y).toFloat()
        val minZ = (box.minZ - cameraPos.z).toFloat()
        val maxX = (box.maxX - cameraPos.x).toFloat()
        val maxY = (box.maxY - cameraPos.y).toFloat()
        val maxZ = (box.maxZ - cameraPos.z).toFloat()
        val entry = matrixStack.peek()

        emitQuad(consumer, entry, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0f, 0f, -1f, alpha)
        emitQuad(consumer, entry, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, 0f, 0f, 1f, alpha)
        emitQuad(consumer, entry, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, -1f, 0f, 0f, alpha)
        emitQuad(consumer, entry, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 1f, 0f, 0f, alpha)
        emitQuad(consumer, entry, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0f, 1f, 0f, alpha)
        emitQuad(consumer, entry, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 0f, -1f, 0f, alpha)
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
        alpha: Int,
    ) {
        consumer.vertex(entry, x1, y1, z1).color(255, 255, 255, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x2, y2, z2).color(255, 255, 255, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x3, y3, z3).color(255, 255, 255, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x4, y4, z4).color(255, 255, 255, alpha).normal(entry, normalX, normalY, normalZ)
    }

    private fun pulsingAlpha(): Int {
        val phase = (System.currentTimeMillis() % PULSE_PERIOD_MILLIS) / PULSE_PERIOD_MILLIS
        val wave = (sin(phase * Math.PI * 2.0) + 1.0) * 0.5
        return (MIN_ALPHA + ((MAX_ALPHA - MIN_ALPHA) * wave)).toInt()
    }
}

package axion.client.render

import com.mojang.blaze3d.addVertex.VertexConsumer
import net.minecraft.client.util.math.Entry
import com.mojang.blaze3d.addVertex.PoseStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object VertexRenderingCompat {
    private val drawOutlineMethod: Method? by lazy {
        net.minecraft.client.renderer.ShapeRenderer::class.java.methods.firstOrNull { method ->
            if (!Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) {
                return@firstOrNull false
            }

            val params = method.parameterTypes
            when (params.size) {
                7 -> params.contentEquals(
                    arrayOf(
                        PoseStack::class.java,
                        VertexConsumer::class.java,
                        VoxelShape::class.java,
                        Double::class.javaPrimitiveType,
                        Double::class.javaPrimitiveType,
                        Double::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    ),
                )

                8 -> params.contentEquals(
                    arrayOf(
                        PoseStack::class.java,
                        VertexConsumer::class.java,
                        VoxelShape::class.java,
                        Double::class.javaPrimitiveType,
                        Double::class.javaPrimitiveType,
                        Double::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType,
                    ),
                )

                else -> false
            }
        }
    }

    // 26.2 deleted ShapeRenderer, so there is no vanilla outline helper to
    // reflect at. The manual path below writes the twelve cuboid edges straight
    // into whatever consumer it is handed, which is exactly what that range
    // needs — its consumer is a recording one that replays through
    // submitCustomGeometry on the correct render type.
    private val useManualOutline: Boolean by lazy { drawOutlineMethod == null }

    private val lineWidthMethod: Method? by lazy {
        VertexConsumer::class.java.methods.firstOrNull { method ->
            (method.name == "setLineWidth" || method.name == "lineWidth") &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == Float::class.javaPrimitiveType
        }
    }

    private val getBoxesMethod: Method? by lazy {
        VoxelShape::class.java.methods.firstOrNull { method ->
            method.parameterCount == 0 &&
                (method.name == "toAabbs" || method.name == "getBoundingBoxes")
        }
    }

    private val drawFilledBoxMethod: Method? by lazy {
        net.minecraft.client.renderer.ShapeRenderer::class.java.methods.firstOrNull { method ->
            if (!Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) {
                return@firstOrNull false
            }

            method.parameterTypes.contentEquals(
                arrayOf(
                    PoseStack::class.java,
                    VertexConsumer::class.java,
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                ),
            )
        }
    }

    fun drawOutline(
        matrixStack: PoseStack,
        consumer: VertexConsumer,
        shape: VoxelShape,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        color: Int,
        lineWidth: Float,
    ) {
        if (useManualOutline) {
            drawOutlineManual(matrixStack, consumer, shape, cameraX, cameraY, cameraZ, color, lineWidth)
            return
        }
        val method = drawOutlineMethod ?: return
        if (method.parameterCount == 8) {
            method.invoke(null, matrixStack, consumer, shape, cameraX, cameraY, cameraZ, color, lineWidth)
        } else {
            method.invoke(null, matrixStack, consumer, shape, cameraX, cameraY, cameraZ, color)
        }
    }

    fun drawOutlineNoOffset(
        matrixStack: PoseStack,
        consumer: VertexConsumer,
        shape: VoxelShape,
        color: Int,
        lineWidth: Float,
    ) {
        if (useManualOutline) {
            drawOutlineManual(matrixStack, consumer, shape, 0.0, 0.0, 0.0, color, lineWidth)
            return
        }
        val method = drawOutlineMethod ?: return
        if (method.parameterCount == 8) {
            method.invoke(null, matrixStack, consumer, shape, 0.0, 0.0, 0.0, color, lineWidth)
        } else {
            method.invoke(null, matrixStack, consumer, shape, 0.0, 0.0, 0.0, color)
        }
    }

    private fun drawOutlineManual(
        matrixStack: PoseStack,
        consumer: VertexConsumer,
        shape: VoxelShape,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        color: Int,
        lineWidth: Float,
    ) {
        val boxes = getBoxesMethod?.invoke(shape) as? List<*> ?: return
        val entry = matrixStack.last()
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val alpha = (color ushr 24) and 0xFF

        for (obj in boxes) {
            val box = obj as? AABB ?: continue
            val minX = outlineCoordinate(box.minX, cameraX)
            val minY = outlineCoordinate(box.minY, cameraY)
            val minZ = outlineCoordinate(box.minZ, cameraZ)
            val maxX = outlineCoordinate(box.maxX, cameraX)
            val maxY = outlineCoordinate(box.maxY, cameraY)
            val maxZ = outlineCoordinate(box.maxZ, cameraZ)

            val nx = 0f
            val ny = 0f
            val nz = 0f

            // 12 edges of a cuboid — bottom, top, then vertical
            emitLine(consumer, entry, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha, lineWidth)
            emitLine(consumer, entry, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha, lineWidth)
            emitLine(consumer, entry, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha, lineWidth)
            emitLine(consumer, entry, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha, lineWidth)

            emitLine(consumer, entry, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha, lineWidth)
            emitLine(consumer, entry, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha, lineWidth)
            emitLine(consumer, entry, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha, lineWidth)
            emitLine(consumer, entry, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha, lineWidth)

            emitLine(consumer, entry, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha, lineWidth)
            emitLine(consumer, entry, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha, lineWidth)
            emitLine(consumer, entry, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha, lineWidth)
            emitLine(consumer, entry, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha, lineWidth)
        }
    }

    /**
     * Matches vanilla ShapeRenderer's offset convention. Callers pass
     * `-camera`, so adding the offset produces camera-relative coordinates.
     */
    fun outlineCoordinate(coordinate: Double, offset: Double): Float =
        (coordinate + offset).toFloat()

    private fun emitLine(
        consumer: VertexConsumer,
        entry: Entry,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        red: Int, green: Int, blue: Int, alpha: Int,
        lineWidth: Float,
    ) {
        val normalX = (x2 - x1).coerceIn(-1f, 1f)
        val normalY = (y2 - y1).coerceIn(-1f, 1f)
        val normalZ = (z2 - z1).coerceIn(-1f, 1f)
        val lwm = lineWidthMethod
        consumer.addVertex(entry, x1, y1, z1).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        lwm?.invoke(consumer, lineWidth)
        consumer.addVertex(entry, x2, y2, z2).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        lwm?.invoke(consumer, lineWidth)
    }

    fun drawFilledBox(
        matrixStack: PoseStack,
        consumer: VertexConsumer,
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ): Boolean {
        val method = drawFilledBoxMethod ?: return false
        method.invoke(
            null,
            matrixStack,
            consumer,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            red,
            green,
            blue,
            alpha,
        )
        return true
    }
}

package axion.client.render

import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.shape.VoxelShape
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object VertexRenderingCompat {
    private val drawOutlineMethod: Method by lazy {
        net.minecraft.client.render.VertexRendering::class.java.methods.firstOrNull { method ->
            if (!Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) {
                return@firstOrNull false
            }

            val params = method.parameterTypes
            when (params.size) {
                7 -> params.contentEquals(
                    arrayOf(
                        MatrixStack::class.java,
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
                        MatrixStack::class.java,
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
        } ?: error("Missing VertexRendering.drawOutline overload")
    }

    private val drawFilledBoxMethod: Method? by lazy {
        net.minecraft.client.render.VertexRendering::class.java.methods.firstOrNull { method ->
            if (!Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) {
                return@firstOrNull false
            }

            method.parameterTypes.contentEquals(
                arrayOf(
                    MatrixStack::class.java,
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
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        shape: VoxelShape,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        color: Int,
        lineWidth: Float,
    ) {
        if (drawOutlineMethod.parameterCount == 8) {
            drawOutlineMethod.invoke(null, matrixStack, consumer, shape, cameraX, cameraY, cameraZ, color, lineWidth)
        } else {
            drawOutlineMethod.invoke(null, matrixStack, consumer, shape, cameraX, cameraY, cameraZ, color)
        }
    }

    fun drawFilledBox(
        matrixStack: MatrixStack,
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

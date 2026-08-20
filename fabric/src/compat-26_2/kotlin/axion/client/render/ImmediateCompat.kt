package axion.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.floats.FloatArrayList
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType

/**
 * 26.2 deleted [net.minecraft.client.renderer.MultiBufferSource] and its
 * `BufferSource`. There is no renamed equivalent: arbitrary world geometry now
 * goes through [SubmitNodeCollector.submitCustomGeometry], which inverts the
 * control flow. Instead of pulling a consumer and writing into it, you hand the
 * engine a callback it replays later, against a render type you name up front.
 *
 * Every Axion renderer reaches its vertex consumer through the [getBuffer]
 * extension below, so that inversion can be absorbed here rather than in ~20
 * renderers: [AxionSubmitBufferSource] hands out recording consumers, batches
 * them per render type, and replays each batch through `submitCustomGeometry`
 * when the frame is flushed.
 */
class AxionSubmitBufferSource(private val collector: SubmitNodeCollector) {
    // Insertion-ordered so replay preserves the order layers were first drawn
    // in, which is what the old BufferSource.endBatch() did.
    private val batches = LinkedHashMap<RenderType, RecordingVertexConsumer>()

    fun getBuffer(renderType: RenderType): VertexConsumer =
        batches.getOrPut(renderType) { RecordingVertexConsumer() }

    /** Mirrors `BufferSource.endBatch()`. */
    fun endBatch() {
        if (batches.isEmpty()) return
        // Axion bakes its transforms into the vertices themselves (every draw
        // goes through addVertex(pose, x, y, z)), so submitting against an
        // identity stack is what keeps the geometry from being transformed
        // twice.
        val identity = PoseStack()
        for ((renderType, recording) in batches) {
            if (recording.isEmpty()) continue
            collector.submitCustomGeometry(identity, renderType) { _, consumer ->
                recording.replayInto(consumer)
            }
        }
        batches.clear()
    }

    fun draw() = endBatch()
}

/**
 * Records the [VertexConsumer] calls Axion makes and replays them verbatim.
 *
 * Only the eight abstract methods are overridden. The interface's defaults
 * (`addVertex(Pose, …)`, `setNormal(Pose, …)`, `setLight`, `setOverlay`, …)
 * funnel into these after applying their transforms, so recording at this level
 * captures the already-transformed values — which is exactly what should be
 * replayed later.
 */
private class RecordingVertexConsumer : VertexConsumer {
    private val opcodes = IntArrayList()
    private val floats = FloatArrayList()
    private val ints = IntArrayList()

    fun isEmpty(): Boolean = opcodes.isEmpty

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        opcodes.add(OP_VERTEX)
        floats.add(x); floats.add(y); floats.add(z)
        return this
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
        opcodes.add(OP_COLOR_RGBA)
        ints.add(red); ints.add(green); ints.add(blue); ints.add(alpha)
        return this
    }

    override fun setColor(packed: Int): VertexConsumer {
        opcodes.add(OP_COLOR_PACKED)
        ints.add(packed)
        return this
    }

    override fun setUv(u: Float, v: Float): VertexConsumer {
        opcodes.add(OP_UV)
        floats.add(u); floats.add(v)
        return this
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer {
        opcodes.add(OP_UV1)
        ints.add(u); ints.add(v)
        return this
    }

    override fun setUv2(u: Int, v: Int): VertexConsumer {
        opcodes.add(OP_UV2)
        ints.add(u); ints.add(v)
        return this
    }

    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer {
        opcodes.add(OP_NORMAL)
        floats.add(x); floats.add(y); floats.add(z)
        return this
    }

    override fun setLineWidth(width: Float): VertexConsumer {
        opcodes.add(OP_LINE_WIDTH)
        floats.add(width)
        return this
    }

    fun replayInto(target: VertexConsumer) {
        var f = 0
        var i = 0
        for (op in 0 until opcodes.size) {
            when (opcodes.getInt(op)) {
                OP_VERTEX -> {
                    target.addVertex(floats.getFloat(f), floats.getFloat(f + 1), floats.getFloat(f + 2))
                    f += 3
                }
                OP_COLOR_RGBA -> {
                    target.setColor(ints.getInt(i), ints.getInt(i + 1), ints.getInt(i + 2), ints.getInt(i + 3))
                    i += 4
                }
                OP_COLOR_PACKED -> {
                    target.setColor(ints.getInt(i))
                    i += 1
                }
                OP_UV -> {
                    target.setUv(floats.getFloat(f), floats.getFloat(f + 1))
                    f += 2
                }
                OP_UV1 -> {
                    target.setUv1(ints.getInt(i), ints.getInt(i + 1))
                    i += 2
                }
                OP_UV2 -> {
                    target.setUv2(ints.getInt(i), ints.getInt(i + 1))
                    i += 2
                }
                OP_NORMAL -> {
                    target.setNormal(floats.getFloat(f), floats.getFloat(f + 1), floats.getFloat(f + 2))
                    f += 3
                }
                OP_LINE_WIDTH -> {
                    target.setLineWidth(floats.getFloat(f))
                    f += 1
                }
            }
        }
    }

    private companion object {
        const val OP_VERTEX = 0
        const val OP_COLOR_RGBA = 1
        const val OP_COLOR_PACKED = 2
        const val OP_UV = 3
        const val OP_UV1 = 4
        const val OP_UV2 = 5
        const val OP_NORMAL = 6
        const val OP_LINE_WIDTH = 7
    }
}

/**
 * Wraps whatever the Fabric render context hands back so the renderers see a
 * uniform buffer source. On 26.2 that is a [SubmitNodeCollector]; every other
 * range already supplies something with `getBuffer`, so this is identity there.
 */
fun adaptConsumers(raw: Any): Any =
    if (raw is SubmitNodeCollector) AxionSubmitBufferSource(raw) else raw

/**
 * Cross-version compatibility extension for getting a buffer from consumers.
 * In 1.21.x (Yarn): Immediate has getBuffer(RenderType)
 * In 26.2 (official): [AxionSubmitBufferSource] stands in for the deleted
 * MultiBufferSource.BufferSource.
 */
fun Any.getBuffer(renderType: RenderType): VertexConsumer {
    if (this is AxionSubmitBufferSource) return getBuffer(renderType)
    val method = this.javaClass.methods.firstOrNull {
        it.name == "getBuffer" && it.parameterCount == 1 && it.parameterTypes[0] == RenderType::class.java
    } ?: error("getBuffer method not found on ${this.javaClass.name}")
    return method.invoke(this, renderType) as? VertexConsumer ?: error("getBuffer returned non-VertexConsumer")
}

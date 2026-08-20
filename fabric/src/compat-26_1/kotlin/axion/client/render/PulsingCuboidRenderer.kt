package axion.client.render
import axion.client.compat.CameraAccess

import axion.client.selection.SelectionBounds
import axion.client.selection.expand
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.Entry
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object PulsingCuboidRenderer {
    private const val DEFAULT_MIN_ALPHA: Int = 52
    private const val DEFAULT_MAX_ALPHA: Int = 76
    private const val PULSE_PERIOD_MILLIS: Double = 2200.0
    // Shared by the box fill and the per-cell contour overlay so the two can
    // never drift out of phase with each other.
    const val SELECTION_PULSE_PERIOD_MILLIS: Double = 5200.0

    // Beam outlines are sized in world units, so scale them with view distance
    // to keep a roughly constant on-screen width like a real line primitive.
    private const val OUTLINE_THICKNESS_PER_UNIT: Double = 0.0013
    private const val MIN_OUTLINE_THICKNESS: Double = 0.01
    private const val MAX_OUTLINE_THICKNESS: Double = 0.06

    // The shell shares the selection fill's colours and signed phase. It used to
    // pulse *white* on its own timer via pulsingAlphaF; under an Iris pack the
    // lightning program it rides is bloom/emissive, which turned that into a
    // bright white flash whenever the red/blue fill crossed through transparent.
    private const val SHELL_BASE_FILL_COLOR: Int = 0xFFCC5656.toInt()
    private const val SHELL_PULSE_FILL_COLOR: Int = 0xFF7C98FF.toInt()

    fun render(
        context: AxionWorldRenderContext,
        box: Box,
        outlineColor: Int,
        lineWidth: Float,
        minAlpha: Int = DEFAULT_MIN_ALPHA,
        maxAlpha: Int = DEFAULT_MAX_ALPHA,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera
        val consumers = context.consumers()
        val cameraPos = CameraAccess.getPos(camera)
        val matrixStack = context.matrices()
        val fillLayer = RenderLayerCompat.shaderSafeQuads()

        val offset = if (context.needsCameraOffset()) cameraPos else Vec3d(0.0, 0.0, 0.0)

        renderFilledBox(
            matrixStack = matrixStack,
            consumer = consumers.getBuffer(fillLayer),
            layer = fillLayer,
            cameraPos = offset,
            box = box,
            alpha = pulsingAlphaF(minAlpha, maxAlpha),
        )
        VertexRenderingCompat.drawOutline(
            matrixStack,
            consumers.getBuffer(RenderLayerCompat.lines()),
            VoxelShapes.cuboid(box),
            -offset.x,
            -offset.y,
            -offset.z,
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
        baseFillColor: Int = SHELL_BASE_FILL_COLOR,
        pulseFillColor: Int = SHELL_PULSE_FILL_COLOR,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera
        val consumers = context.consumers()
        val cameraPos = CameraAccess.getPos(camera)
        val matrixStack = context.matrices()
        // Same signed phase as the selection fill: one colour at a time,
        // fading to the configured visible floor at the crossover.
        val shellPhase = selectionPulsePhase(SELECTION_PULSE_PERIOD_MILLIS)
        val alpha = PreviewVisualPolicy.pulseAlpha(minAlpha, maxAlpha, shellPhase)
        val fillLayer = RenderLayerCompat.lightning()
        val consumer = consumers.getBuffer(fillLayer)

        val offset = if (context.needsCameraOffset()) cameraPos else Vec3d(0.0, 0.0, 0.0)

        renderFilledBox(
            matrixStack = matrixStack,
            consumer = consumer,
            layer = fillLayer,
            cameraPos = offset,
            box = SelectionBounds.outlineBox(box),
            alpha = alpha,
            color = if (shellPhase >= 0f) pulseFillColor else baseFillColor,
        )

        VertexRenderingCompat.drawOutline(
            matrixStack,
            consumers.getBuffer(RenderLayerCompat.lines()),
            VoxelShapes.cuboid(SelectionBounds.outlineBox(box)),
            -offset.x,
            -offset.y,
            -offset.z,
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
        val camera = client.gameRenderer.camera
        val consumers = context.consumers()
        val cameraPos = CameraAccess.getPos(camera)
        val matrixStack = context.matrices()
        val baseBox = SelectionBounds.outlineBox(box)
        val pulseBox = baseBox.expand(0.0015, 0.0015, 0.0015)
        // Persistent GPU previews draw after this cuboid is queued. Using the
        // vanilla debug layer here would write the cuboid's near face into the
        // depth buffer and mask every preview fragment behind it.
        val fillLayer = RenderLayerCompat.xrayQuads()
        val filledConsumer = consumers.getBuffer(fillLayer)

        val offset = if (context.needsCameraOffset()) cameraPos else Vec3d(0.0, 0.0, 0.0)

        // One fill phasing baseFillColor -> visible floor -> pulseFillColor,
        // rather than a constant base with a pulse stacked on top.
        val fillPhase = selectionPulsePhase(SELECTION_PULSE_PERIOD_MILLIS)
        renderFilledBox(
            matrixStack = matrixStack,
            consumer = filledConsumer,
            layer = fillLayer,
            cameraPos = offset,
            box = pulseBox,
            alpha = PreviewVisualPolicy.pulseAlpha(
                minAlpha = maxOf(baseAlpha, pulseMinAlpha),
                maxAlpha = pulseMaxAlpha,
                signedPhase = fillPhase,
            ),
            color = if (fillPhase >= 0f) pulseFillColor else baseFillColor,
        )
        renderXrayOutline(
            matrixStack = matrixStack,
            consumer = filledConsumer,
            layer = fillLayer,
            originOffset = offset,
            viewPos = cameraPos,
            box = pulseBox,
            color = outlineColor,
            lineWidth = lineWidth,
        )
    }

    fun renderOutlineBox(
        context: AxionWorldRenderContext,
        box: Box,
        outlineColor: Int,
        lineWidth: Float,
    ) {
        val client = MinecraftClient.getInstance()
        val camera = client.gameRenderer.camera
        val consumers = context.consumers()
        val cameraPos = CameraAccess.getPos(camera)

        val offset = if (context.needsCameraOffset()) cameraPos else Vec3d(0.0, 0.0, 0.0)

        val fillLayer = RenderLayerCompat.xrayQuads()
        renderXrayOutline(
            matrixStack = context.matrices(),
            consumer = consumers.getBuffer(fillLayer),
            layer = fillLayer,
            originOffset = offset,
            viewPos = cameraPos,
            box = SelectionBounds.outlineBox(box),
            color = outlineColor,
            lineWidth = lineWidth,
        )
    }

    /**
     * The vanilla lines layer bakes its depth test into the render pipeline, so
     * toggling GL depth state around the draw cannot make an outline appear
     * through terrain -- only the fills, which already sit on a no-depth layer,
     * showed through. Emitting the twelve edges as thin quads on that same
     * layer makes the outline behave like the symmetry gizmo.
     */
    fun renderXrayOutline(
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        layer: RenderLayer,
        originOffset: Vec3d,
        viewPos: Vec3d,
        box: Box,
        color: Int,
        lineWidth: Float,
    ) {
        val alpha = ((color ushr 24) and 0xFF).let { if (it == 0) 255 else it }
        forEachEdge(box) { edge ->
            val half = beamHalfThickness(viewPos, edge, lineWidth)
            renderFilledBox(
                matrixStack = matrixStack,
                consumer = consumer,
                layer = layer,
                cameraPos = originOffset,
                box = edge.expand(half, half, half),
                alpha = alpha,
                color = color,
            )
        }
    }

    private fun beamHalfThickness(viewPos: Vec3d, edge: Box, lineWidth: Float): Double {
        val dx = ((edge.minX + edge.maxX) * 0.5) - viewPos.x
        val dy = ((edge.minY + edge.maxY) * 0.5) - viewPos.y
        val dz = ((edge.minZ + edge.maxZ) * 0.5) - viewPos.z
        val distance = sqrt((dx * dx) + (dy * dy) + (dz * dz))
        val width = distance * OUTLINE_THICKNESS_PER_UNIT * lineWidth.coerceAtLeast(1f)
        return width.coerceIn(MIN_OUTLINE_THICKNESS, MAX_OUTLINE_THICKNESS) * 0.5
    }

    /** The twelve edges of [box] as degenerate boxes, ready to be inflated into beams. */
    private inline fun forEachEdge(box: Box, action: (Box) -> Unit) {
        action(Box(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ))
        action(Box(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ))
        action(Box(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ))
        action(Box(box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ))

        action(Box(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ))
        action(Box(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ))
        action(Box(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ))
        action(Box(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ))

        action(Box(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ))
        action(Box(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ))
        action(Box(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ))
        action(Box(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ))
    }

    fun renderFilledBox(
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        layer: RenderLayer,
        cameraPos: Vec3d,
        box: Box,
        alpha: Int,
        color: Int = 0xFFFFFFFF.toInt(),
    ) = renderFilledBox(matrixStack, consumer, layer, cameraPos, box, alpha.toFloat(), color)

    /**
     * Fractional-alpha variant used by the pulses.
     *
     * Vertex alpha is a byte, so a pulse over a narrow configured range only
     * has a handful of distinct steps,
     * and the sine spends most of its time near the extremes where it changes
     * slowest -- so the pulse visibly jumps between levels instead of easing.
     * Blending adds `rgb * alpha`, so rounding alpha *up* and scaling RGB down
     * by the leftover keeps that product moving continuously while leaving whole
     * alpha values (every non-pulsing caller) byte-for-byte unchanged.
     */
    fun renderFilledBox(
        matrixStack: MatrixStack,
        consumer: VertexConsumer,
        layer: RenderLayer,
        cameraPos: Vec3d,
        box: Box,
        alpha: Float,
        color: Int = 0xFFFFFFFF.toInt(),
    ) {
        // The fractional-alpha ease rounds alpha *up* and scales RGB down, so
        // near the pulse crossover it emits quads with alpha 1 and near-black
        // RGB. Vanilla blending renders that as invisible, but shader-pack
        // programs make their own assumptions about vertex colour (some
        // un-premultiply by alpha, some bloom on ratios), and that degenerate
        // combination can blow out to a solid white flash. Under a pack, use
        // plain byte alpha with unscaled RGB: sub-1 alpha draws nothing. The
        // selection pulse policy itself keeps its fills above a visible floor.
        val shaderPackActive = ShaderPackCompat.isShaderPackActive()
        val alphaByte = if (shaderPackActive) {
            alpha.roundToInt().coerceIn(0, 255)
        } else {
            ceil(alpha).toInt().coerceIn(0, 255)
        }
        if (alphaByte == 0) {
            return
        }
        val colorScale = if (shaderPackActive) 1f else (alpha / alphaByte).coerceIn(0f, 1f)
        val minX = (box.minX - cameraPos.x).toFloat()
        val minY = (box.minY - cameraPos.y).toFloat()
        val minZ = (box.minZ - cameraPos.z).toFloat()
        val maxX = (box.maxX - cameraPos.x).toFloat()
        val maxY = (box.maxY - cameraPos.y).toFloat()
        val maxZ = (box.maxZ - cameraPos.z).toFloat()
        val entry = matrixStack.peek()
        val red = (((color shr 16) and 0xFF) * colorScale).roundToInt()
        val green = (((color shr 8) and 0xFF) * colorScale).roundToInt()
        val blue = ((color and 0xFF) * colorScale).roundToInt()

        val drawMode = layer.drawMode

        // Cull back faces. Vertices are camera-relative, so the camera is at the
        // origin and a face is only visible when the camera is on its outer side
        // (its plane coordinate has the matching sign). Emitting all six stacks
        // the far translucent walls behind the near ones and muddies the fill;
        // this leaves only the walls actually facing the viewer, so you look
        // cleanly into the selection like Axiom's box. When the camera is level
        // with the box on an axis, neither wall on it is drawn -- you see through.
        if (minZ > 0f) emitFace(consumer, drawMode, entry, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0f, 0f, -1f, red, green, blue, alphaByte)
        if (maxZ < 0f) emitFace(consumer, drawMode, entry, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, 0f, 0f, 1f, red, green, blue, alphaByte)
        if (minX > 0f) emitFace(consumer, drawMode, entry, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, -1f, 0f, 0f, red, green, blue, alphaByte)
        if (maxX < 0f) emitFace(consumer, drawMode, entry, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 1f, 0f, 0f, red, green, blue, alphaByte)
        if (maxY < 0f) emitFace(consumer, drawMode, entry, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0f, 1f, 0f, red, green, blue, alphaByte)
        if (minY > 0f) emitFace(consumer, drawMode, entry, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 0f, -1f, 0f, red, green, blue, alphaByte)

        // Lightning culls back faces, so it needs paired windings for a shell
        // that remains visible from inside and outside. Debug/x-ray layers are
        // already no-cull and deliberately keep one winding to avoid blending
        // the same translucent plane twice.
        if (RenderLayerCompat.requiresReverseWinding(layer)) {
            emitFace(consumer, drawMode, entry, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, 0f, 0f, 1f, red, green, blue, alphaByte)
            emitFace(consumer, drawMode, entry, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, 0f, 0f, -1f, red, green, blue, alphaByte)
            emitFace(consumer, drawMode, entry, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, 1f, 0f, 0f, red, green, blue, alphaByte)
            emitFace(consumer, drawMode, entry, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, -1f, 0f, 0f, red, green, blue, alphaByte)
            emitFace(consumer, drawMode, entry, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, 0f, -1f, 0f, red, green, blue, alphaByte)
            emitFace(consumer, drawMode, entry, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, minX, minY, minZ, 0f, 1f, 0f, red, green, blue, alphaByte)
        }
    }

    private fun emitFace(
        consumer: VertexConsumer,
        drawMode: VertexFormat.Mode,
        entry: Entry,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        normalX: Float, normalY: Float, normalZ: Float,
        red: Int, green: Int, blue: Int, alpha: Int,
    ) {
        when (drawMode) {
            VertexFormat.Mode.QUADS -> emitQuad(
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
        consumer: VertexConsumer, entry: Entry,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        normalX: Float, normalY: Float, normalZ: Float,
        red: Int, green: Int, blue: Int, alpha: Int,
    ) {
        consumer.vertex(entry, x1, y1, z1).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x2, y2, z2).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x3, y3, z3).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x4, y4, z4).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
    }

    private fun emitTriangles(
        consumer: VertexConsumer, entry: Entry,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        normalX: Float, normalY: Float, normalZ: Float,
        red: Int, green: Int, blue: Int, alpha: Int,
    ) {
        emitTriangle(consumer, entry, x1, y1, z1, x2, y2, z2, x3, y3, z3, normalX, normalY, normalZ, red, green, blue, alpha)
        emitTriangle(consumer, entry, x1, y1, z1, x3, y3, z3, x4, y4, z4, normalX, normalY, normalZ, red, green, blue, alpha)
    }

    private fun emitTriangle(
        consumer: VertexConsumer, entry: Entry,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        normalX: Float, normalY: Float, normalZ: Float,
        red: Int, green: Int, blue: Int, alpha: Int,
    ) {
        consumer.vertex(entry, x1, y1, z1).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x2, y2, z2).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
        consumer.vertex(entry, x3, y3, z3).color(red, green, blue, alpha).normal(entry, normalX, normalY, normalZ)
    }

    fun pulsingAlpha(
        minAlpha: Int,
        maxAlpha: Int,
        periodMillis: Double = PULSE_PERIOD_MILLIS,
    ): Int = pulsingAlphaF(minAlpha, maxAlpha, periodMillis).roundToInt()

    /**
     * Un-quantised pulse alpha. Pair with the [renderFilledBox] Float overload so
     * the wave is not flattened to a handful of byte steps.
     *
     * Uses the monotonic clock: wall-clock time can be stepped by NTP mid-pulse,
     * which shows up as the box snapping to a different brightness.
     */
    /**
     * Signed pulse phase in -1..1.
     *
     * The selection used to draw a constant base fill with a second pulse fill
     * stacked on top. Two translucent layers can never reach zero alpha, so the
     * selection always read as a muddy blend of both colours, and each layer the
     * view passes through compounds -- which is what made deep selections look
     * blurred. A signed phase drives a single fill that reaches each colour at
     * full strength, while the visual policy keeps a visible alpha floor at
     * the colour crossover.
     */
    fun selectionPulsePhase(periodMillis: Double = PULSE_PERIOD_MILLIS): Float {
        val periodNanos = (periodMillis * 1_000_000.0).toLong().coerceAtLeast(1L)
        val progress = System.nanoTime().mod(periodNanos).toDouble() / periodNanos
        return sin(progress * Math.PI * 2.0).toFloat()
    }

    fun pulsingAlphaF(
        minAlpha: Int,
        maxAlpha: Int,
        periodMillis: Double = PULSE_PERIOD_MILLIS,
    ): Float {
        val periodNanos = (periodMillis * 1_000_000.0).toLong().coerceAtLeast(1L)
        val phase = System.nanoTime().mod(periodNanos).toDouble() / periodNanos
        val wave = (sin(phase * Math.PI * 2.0) + 1.0) * 0.5
        return (minAlpha + ((maxAlpha - minAlpha) * wave)).toFloat()
    }
}

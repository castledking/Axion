package axion.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.ShapeRenderer
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape
import axion.client.render.gpu.PreviewOcclusionCompat

// --- MinecraftClient / Minecraft ---
val Minecraft.world get() = level
val Minecraft.crosshairTarget get() = hitResult
val Minecraft.inGameHud get() = gui
val Minecraft.framebuffer get() = mainRenderTarget
val Minecraft.server get() = singleplayerServer

// --- GameRenderer ---
val GameRenderer.camera get() = mainCamera

// --- PoseStack (MatrixStack) ---
fun PoseStack.push() = pushPose()
fun PoseStack.pop() = popPose()
fun PoseStack.peek(): PoseStack.Pose = last()

// --- VertexConsumer (call-site bridges) ---
fun VertexConsumer.vertex(x: Float, y: Float, z: Float): VertexConsumer = addVertex(x, y, z)
fun VertexConsumer.vertex(x: Double, y: Double, z: Double): VertexConsumer = addVertex(x.toFloat(), y.toFloat(), z.toFloat())
fun VertexConsumer.vertex(entry: PoseStack.Pose, x: Float, y: Float, z: Float): VertexConsumer = addVertex(entry, x, y, z)
fun VertexConsumer.color(r: Int, g: Int, b: Int, a: Int): VertexConsumer = setColor(r, g, b, a)
fun VertexConsumer.color(r: Float, g: Float, b: Float, a: Float): VertexConsumer = setColor(r, g, b, a)
fun VertexConsumer.texture(u: Float, v: Float): VertexConsumer = setUv(u, v)
fun VertexConsumer.overlay(u: Int, v: Int): VertexConsumer = setUv1(u, v)
fun VertexConsumer.light(u: Int, v: Int): VertexConsumer = setUv2(u, v)
fun VertexConsumer.normal(x: Float, y: Float, z: Float): VertexConsumer = setNormal(x, y, z)
fun VertexConsumer.normal(entry: PoseStack.Pose, x: Float, y: Float, z: Float): VertexConsumer = setNormal(entry, x, y, z)

// --- MultiBufferSource.BufferSource (draw → endBatch) ---
fun MultiBufferSource.BufferSource.draw() = endBatch()

// --- Direction ---
val Direction.vector: Vec3i get() = unitVec3i
val Direction.offsetX: Int get() = stepX
val Direction.offsetY: Int get() = stepY
val Direction.offsetZ: Int get() = stepZ

// --- VoxelShape ---
fun VoxelShape.offset(x: Double, y: Double, z: Double): VoxelShape = move(x, y, z)

// --- RenderType (RenderLayer) ---
val net.minecraft.client.renderer.rendertype.RenderType.drawMode: com.mojang.blaze3d.vertex.VertexFormat.Mode get() = mode()

// --- Vec3 (Vec3d) ---
fun net.minecraft.world.phys.Vec3.multiply(value: Double): net.minecraft.world.phys.Vec3 = scale(value)

// --- Entity ---
val net.minecraft.world.entity.Entity.rotationVecClient: net.minecraft.world.phys.Vec3 get() = getViewVector(1.0f)

// --- Block ---
val Block.defaultState: BlockState get() = defaultBlockState()

// --- BlockState ---
val BlockState.isOpaqueFullCube: Boolean get() = PreviewOcclusionCompat.isOpaqueFullCube(this)
val BlockState.soundGroup get() = soundType

// --- ShapeRenderer (VertexRendering) bridge ---
object VertexRendering {
    fun drawOutline(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        shape: VoxelShape,
        x: Double,
        y: Double,
        z: Double,
        color: Int,
        lineWidth: Float,
    ) {
        ShapeRenderer.renderShape(poseStack, consumer, shape, x, y, z, color, lineWidth)
    }

    fun drawOutlineNoOffset(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        shape: VoxelShape,
        color: Int,
        lineWidth: Float,
    ) {
        // 26.1: ShapeRenderer.renderShape with no offset (poseStack already in world coordinates)
        ShapeRenderer.renderShape(poseStack, consumer, shape, 0.0, 0.0, 0.0, color, lineWidth)
    }
}

val BlockState.renderTypeCompat: net.minecraft.world.level.block.RenderShape get() = getRenderShape()

fun BlockState.getRenderingSeedCompat(pos: net.minecraft.core.BlockPos): Long = getSeed(pos)

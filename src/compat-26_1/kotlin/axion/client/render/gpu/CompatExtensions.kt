package axion.client.render.gpu

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.world.level.block.state.BlockState

val Minecraft.world get() = level
val Minecraft.framebuffer get() = mainRenderTarget

val GameRenderer.camera get() = mainCamera

fun PoseStack.push() = pushPose()
fun PoseStack.pop() = popPose()
fun PoseStack.peek(): PoseStack.Pose = last()

val BlockState.isOpaqueFullCube: Boolean get() = PreviewOcclusionCompat.isOpaqueFullCube(this)

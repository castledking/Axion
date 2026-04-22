package axion.client.render

import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos

/**
 * Renders preview blocks using AxionPreviewMeshCache for surface-block filtering
 * and caching, and AxionBlockTessellator for efficient batch tessellation.
 * Uses TemplateBlockRenderView so face culling works against preview neighbors
 * only (not real world), eliminating inner-face bleeding through transparency.
 */
object PreviewShellBlockRenderer {
    private const val MAX_RENDERED_BLOCKS: Int = 32768

    fun render(
        context: AxionWorldRenderContext,
        clipboard: ClipboardBuffer,
        origins: Collection<BlockPos>,
        color: Int,
        alpha: Int,
    ): Boolean {
        if (origins.isEmpty()) {
            return false
        }

        val client = MinecraftClient.getInstance()
        val world = client.world ?: return false
        val camera = client.gameRenderer.camera ?: return false
        val cameraPos = camera.cameraPos
        val matrixStack = context.matrices()
        val alphaScale = alpha / 255.0f

        val cachedMesh = AxionPreviewMeshCache.getOrBuild(
            clipboard = clipboard,
            origins = origins,
            color = color,
            alpha = alpha,
            scale = 1.0f,
            maxBlocks = MAX_RENDERED_BLOCKS,
        )

        if (cachedMesh == null || cachedMesh.blocks.isEmpty()) {
            return false
        }

        val previewView = AxionBlockTessellator.TemplateBlockRenderView(world, cachedMesh.statesByPosition)
        val consumer = TintedAlphaVertexConsumer(
            context.consumers().getBuffer(RenderLayerCompat.blockTranslucentCull()),
            alphaScale,
            color,
        )

        val rendered = AxionBlockTessellator.tessellateBatch(
            blocks = cachedMesh.blocks,
            world = previewView,
            matrixStack = matrixStack,
            consumer = consumer,
            cameraX = cameraPos.x,
            cameraY = cameraPos.y,
            cameraZ = cameraPos.z,
            checkSides = true,
        )

        return rendered > 0
    }
}

package axion.mixin.client

import axion.client.editor.AxionEditorMode
import axion.client.editor.EditorFrameController
import axion.client.editor.EditorFramePlatform
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.Framebuffer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * 1.21.5 present: Framebuffer.blitToScreen() with the pre-1.21.6 plain
 * RenderSystem scissor pair.
 */
@Mixin(Framebuffer::class)
abstract class LegacyRenderTargetFrameMixin {
    @Inject(method = ["blitToScreen"], at = [At("HEAD")])
    private fun axionFrameBlitBegin(ci: CallbackInfo) {
        if (!AxionEditorMode.isActive() || !EditorFramePlatform.supportsFraming) {
            return
        }
        val client = MinecraftClient.getInstance()
        if (client.framebuffer !== this) {
            return
        }
        val f = EditorFrameController
        if (f.frameWidthPx < 2 || f.frameHeightPx < 2) {
            return
        }
        RenderSystem.enableScissor(
            f.frameXpx,
            f.frameYpxGl,
            f.frameWidthPx,
            f.frameHeightPx,
        )
    }

    @Inject(method = ["blitToScreen"], at = [At("RETURN")])
    private fun axionFrameBlitEnd(ci: CallbackInfo) {
        if (!AxionEditorMode.isActive() || !EditorFramePlatform.supportsFraming) {
            return
        }
        val client = MinecraftClient.getInstance()
        if (client.framebuffer !== this) {
            return
        }
        RenderSystem.disableScissor()
    }
}

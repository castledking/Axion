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
 * Axiom-style present: the main framebuffer is only the size of the inner
 * editor frame while framing is active (the window mixin lies about the
 * dimensions), so the fullscreen present is clipped into the frame's screen
 * rectangle instead of stretching across the window. Margins keep the last
 * frame's chrome until the editor overlay repaints them this frame.
 */
@Mixin(Framebuffer::class)
abstract class RenderTargetFrameMixin {
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
        RenderSystem.enableScissorForRenderTypeDraws(
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
        RenderSystem.disableScissorForRenderTypeDraws()
    }
}

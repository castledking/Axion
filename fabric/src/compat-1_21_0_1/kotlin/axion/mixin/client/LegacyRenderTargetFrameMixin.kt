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
 * 1.21.0-1.21.4 present: Framebuffer.draw(int, int[, boolean]). The scissor
 * wrap clips the fullscreen draw into the editor frame's screen rectangle.
 */
@Mixin(Framebuffer::class)
abstract class LegacyRenderTargetFrameMixin {
    @Inject(method = ["draw"], at = [At("HEAD")])
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

    @Inject(method = ["draw"], at = [At("RETURN")])
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

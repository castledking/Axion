package axion.mixin.client

import axion.client.editor.EditorFrameController
import axion.client.editor.AxionEditorMode
import axion.client.editor.EditorFramePlatform
import net.minecraft.client.util.Window
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Axiom-style frame squish: while the editor owns input, the window reports
 * only the inner frame rectangle (between the panels) as its framebuffer and
 * GUI dimensions. Vanilla then resizes its main target to that size, lays out
 * chat/HUD/screens inside it, and presents a small image which the renderer
 * mixin places at the frame's position on the real screen.
 */
@Mixin(Window::class)
abstract class WindowFrameMixin {
    @Shadow
    @Final
    private var framebufferWidth: Int = 0

    @Shadow
    @Final
    private var framebufferHeight: Int = 0

    @Shadow
    @Final
    private var width: Int = 0

    @Shadow
    @Final
    private var height: Int = 0

    @Shadow
    @Final
    private var handle: Long = 0

    @Shadow
    private var scaleFactor: Double = 0.0

    @Shadow
    private var scaledWidth: Int = 0

    @Shadow
    private var scaledHeight: Int = 0

    @Unique
    private fun framing(): Boolean =
        AxionEditorMode.isActive() && EditorFramePlatform.supportsFraming

    @Unique
    private fun refreshGeometry() {
        EditorFrameController.update(
            framebufferWidth,
            framebufferHeight,
            width,
            height,
            scaledWidth,
            scaledHeight,
        )
    }

    @Unique
    private fun widthRatio(): Float =
        (framebufferWidth.toFloat() / width.toFloat()).coerceIn(0.125f, 8f)

    @Unique
    private fun heightRatio(): Float =
        (framebufferHeight.toFloat() / height.toFloat()).coerceIn(0.125f, 8f)

    @Unique
    private fun framedFramebufferWidth(): Int {
        refreshGeometry()
        return (EditorFrameController.frameWidthGui * widthRatio()).toInt().coerceAtLeast(2)
    }

    @Unique
    private fun framedFramebufferHeight(): Int {
        refreshGeometry()
        return (EditorFrameController.frameHeightGui * heightRatio()).toInt().coerceAtLeast(2)
    }

    @Inject(method = ["getFramebufferWidth"], at = [At("HEAD")], cancellable = true)
    private fun axionFrameFramebufferWidth(cir: CallbackInfoReturnable<Int>) {
        if (framing()) {
            cir.returnValue = framedFramebufferWidth()
        }
    }

    @Inject(method = ["getFramebufferHeight"], at = [At("HEAD")], cancellable = true)
    private fun axionFrameFramebufferHeight(cir: CallbackInfoReturnable<Int>) {
        if (framing()) {
            cir.returnValue = framedFramebufferHeight()
        }
    }

    @Inject(method = ["getWidth"], at = [At("HEAD")], cancellable = true)
    private fun axionFrameWindowWidth(cir: CallbackInfoReturnable<Int>) {
        if (framing()) {
            cir.returnValue = framedFramebufferWidth()
        }
    }

    @Inject(method = ["getHeight"], at = [At("HEAD")], cancellable = true)
    private fun axionFrameWindowHeight(cir: CallbackInfoReturnable<Int>) {
        if (framing()) {
            cir.returnValue = framedFramebufferHeight()
        }
    }

    @Inject(method = ["calculateScaleFactor"], at = [At("HEAD")], cancellable = true)
    private fun axionFrameCalculateScale(scale: Int, forceEven: Boolean, cir: CallbackInfoReturnable<Int>) {
        if (!framing()) {
            return
        }
        refreshGeometry()
        var fbw = framedFramebufferWidth()
        var fbh = framedFramebufferHeight()

        var j = 1
        while (j != scale && j < fbw && j < fbh && fbw / (j + 1) >= 320 && fbh / (j + 1) >= 240) {
            j += 1
        }
        if (forceEven && j % 2 != 0) {
            j += 1
        }
        cir.returnValue = j
    }

    @Inject(method = ["setScaleFactor"], at = [At("HEAD")], cancellable = true)
    private fun axionFrameSetScaleFactor(newScale: Double, ci: CallbackInfo) {
        if (!framing()) {
            return
        }
        refreshGeometry()
        val fbw = framedFramebufferWidth()
        val fbh = framedFramebufferHeight()

        this.scaleFactor = newScale
        val i = (fbw / newScale).toInt()
        this.scaledWidth = if (fbw / newScale > i) i + 1 else i
        val j = (fbh / newScale).toInt()
        this.scaledHeight = if (fbh / newScale > j) j + 1 else j
        ci.cancel()
    }
}

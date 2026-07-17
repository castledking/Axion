package axion.mixin.client

import axion.client.mode.ClientModeController
import axion.client.mode.NoClipVisualPolicy
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.Camera
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Lets third-person cameras keep their requested distance while passing through terrain. */
@Mixin(Camera::class)
abstract class CameraNoClipMixin {
    // Yarn 1.21.x name.
    @Inject(method = ["clipToSpace"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionKeepNoClipCameraDistanceYarn(
        requestedDistance: Float,
        cir: CallbackInfoReturnable<Float>,
    ) {
        applyNoClipDistance(requestedDistance, cir)
    }

    // Official 1.21.9+ / 26.1.x name.
    @Inject(method = ["getMaxZoom"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionKeepNoClipCameraDistanceOfficial(
        requestedDistance: Float,
        cir: CallbackInfoReturnable<Float>,
    ) {
        applyNoClipDistance(requestedDistance, cir)
    }

    private fun applyNoClipDistance(requestedDistance: Float, cir: CallbackInfoReturnable<Float>) {
        val player = MinecraftClient.getInstance().player ?: return
        val override = NoClipVisualPolicy.cameraDistanceOverride(
            requestedDistance = requestedDistance,
            noClipActive = ClientModeController.isNoClipActiveFor(player),
        )
        if (override != null) {
            cir.returnValue = override
        }
    }
}

package axion.mixin.client

import axion.client.mode.ClientModeController
import net.minecraft.client.network.ClientPlayerEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPlayerEntity::class)
abstract class ClientPlayerEntityMixin {
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun self(): ClientPlayerEntity = this as ClientPlayerEntity

    @Inject(method = ["tickMovement"], at = [At("HEAD")], require = 0)
    private fun axionApplyNoClipBeforeMovement(ci: CallbackInfo) {
        val player = self()
        if (!ClientModeController.isNoClipActiveFor(player)) {
            return
        }

        // 26.1.x removed the mapped noClip setter this mixin used; push-out suppression
        // still handles client-side collision nudging until the movement hook is ported.
        // player.noClip = true
    }

    @Inject(method = ["pushOutOfBlocks"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressClientPushOutOfBlocks(x: Double, z: Double, ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        ci.cancel()
    }
}

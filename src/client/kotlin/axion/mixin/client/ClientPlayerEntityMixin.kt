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

    @Inject(method = ["tickMovement"], at = [At("HEAD")])
    private fun axionApplyNoClipBeforeMovement(ci: CallbackInfo) {
        val player = self()
        if (!ClientModeController.isNoClipActiveFor(player)) {
            return
        }

        player.noClip = true
    }

    @Inject(method = ["pushOutOfBlocks"], at = [At("HEAD")], cancellable = true)
    private fun axionSuppressClientPushOutOfBlocks(x: Double, z: Double, ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        ci.cancel()
    }
}

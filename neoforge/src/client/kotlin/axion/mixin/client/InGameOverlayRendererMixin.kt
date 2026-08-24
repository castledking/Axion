package axion.mixin.client

import axion.client.mode.ClientModeController
import axion.client.mode.NoClipVisualPolicy
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.client.renderer.ScreenEffectRenderer
import net.minecraft.world.entity.player.Player
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Removes only the opaque in-wall overlay; water and fire overlays remain vanilla. */
@Mixin(ScreenEffectRenderer::class)
abstract class InGameOverlayRendererMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["getInWallBlockState"], at = [At("HEAD")], cancellable = true, require = 0)
        private fun axionHideInWallOverlayYarn(
            player: Player,
            cir: CallbackInfoReturnable<BlockState?>,
        ) {
            suppressForNoClip(player, cir)
        }

        @JvmStatic
        @Inject(method = ["getViewBlockingState"], at = [At("HEAD")], cancellable = true, require = 0)
        private fun axionHideInWallOverlayOfficial(
            player: Player,
            cir: CallbackInfoReturnable<BlockState?>,
        ) {
            suppressForNoClip(player, cir)
        }

        private fun suppressForNoClip(player: Player, cir: CallbackInfoReturnable<BlockState?>) {
            val suppress = NoClipVisualPolicy.suppressInWallOverlay(
                noClipActive = ClientModeController.isNoClipActiveFor(player),
            )
            if (suppress) {
null
            }
        }
    }
}

package axion.mixin.client

import axion.client.input.AxionInteractionRouter
import axion.client.mode.ClientModeController
import axion.client.symmetry.SymmetryBreakController
import net.minecraft.client.MinecraftClient
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(MinecraftClient::class)
abstract class MinecraftClientMixin {
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun self(): MinecraftClient = this as MinecraftClient

    // Yarn name: doAttack (1.21.x)
    @Inject(method = ["doAttack"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandlePrimaryAction(ci: CallbackInfoReturnable<Boolean>) {
        axionHandlePrimaryActionImpl(ci)
    }

    // 26.1.x official namespace: startAttack
    @Inject(method = ["startAttack"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandlePrimaryActionOfficial(ci: CallbackInfoReturnable<Boolean>) {
        axionHandlePrimaryActionImpl(ci)
    }

    private fun axionHandlePrimaryActionImpl(ci: CallbackInfoReturnable<Boolean>) {
        // Manually track attack key press since cancelling prevents vanilla key binding updates
        ClientModeController.setAttackKeyManuallyPressed()

        SymmetryBreakController.handlePrimaryAction(self())

        // Handle bulldozer + infinite reach multi-block breaking
        if (ClientModeController.handleBulldozerInfiniteReachBreaking(self())) {
            ci.setReturnValue(false)
            ci.cancel()
            return
        }

        // Handle infinite reach block breaking at vanilla speed
        if (ClientModeController.handleInfiniteReachBreaking(self())) {
            ci.setReturnValue(false)
            ci.cancel()
            return
        }

        if (AxionInteractionRouter.shouldSuppressPrimary(self())) {
            ci.setReturnValue(false)
            ci.cancel()
            return
        }

        if (AxionInteractionRouter.consumePrimaryAction(self())) {
            ci.setReturnValue(false)
            ci.cancel()
            return
        }

        if (ClientModeController.shouldSuppressPrimary(self())) {
            ci.setReturnValue(false)
            ci.cancel()
            return
        }

        if (ClientModeController.consumePrimaryAction(self())) {
            ci.setReturnValue(false)
            ci.cancel()
        }
    }

    // Yarn name: doItemUse (1.21.x)
    @Inject(method = ["doItemUse"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandleSecondaryAction(ci: CallbackInfo) {
        axionHandleSecondaryActionImpl(ci)
    }

    // 26.1.x official namespace: startUseItem
    @Inject(method = ["startUseItem"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandleSecondaryActionOfficial(ci: CallbackInfo) {
        axionHandleSecondaryActionImpl(ci)
    }

    private fun axionHandleSecondaryActionImpl(ci: CallbackInfo) {
        // Manually track use key press since cancelling prevents vanilla key binding updates
        ClientModeController.setUseKeyManuallyPressed()

        // Handle fast place + infinite reach multi-block placement
        if (ClientModeController.handleFastPlaceInfiniteReachPlacement(self())) {
            ci.cancel()
            return
        }

        // Handle infinite reach + vanilla-speed placement in the mixin
        // This bypasses vanilla's item use cooldown
        if (ClientModeController.handleInfiniteReachPlacement(self())) {
            ci.cancel()
            return
        }

        if (AxionInteractionRouter.shouldSuppressSecondary(self())) {
            ci.cancel()
            return
        }

        if (AxionInteractionRouter.consumeSecondaryAction(self())) {
            ci.cancel()
            return
        }

        if (ClientModeController.shouldSuppressSecondary(self())) {
            ci.cancel()
            return
        }

        if (ClientModeController.consumeSecondaryAction(self())) {
            ci.cancel()
        }
    }

    // Yarn name: handleBlockBreaking (1.21.x)
    @Inject(method = ["handleBlockBreaking"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandleBlockBreaking(breaking: Boolean, ci: CallbackInfo) {
        axionHandleBlockBreakingImpl(breaking, ci)
    }

    // 26.1.x official namespace: continueAttack
    @Inject(method = ["continueAttack"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandleBlockBreakingOfficial(breaking: Boolean, ci: CallbackInfo) {
        axionHandleBlockBreakingImpl(breaking, ci)
    }

    private fun axionHandleBlockBreakingImpl(breaking: Boolean, ci: CallbackInfo) {
        if (AxionInteractionRouter.shouldSuppressPrimary(self())) {
            ci.cancel()
            return
        }

        if (ClientModeController.shouldSuppressPrimary(self())) {
            ci.cancel()
            return
        }

        if (breaking && AxionInteractionRouter.ownsPrimaryAction()) {
            ci.cancel()
            return
        }

        if (breaking && ClientModeController.consumeHeldPrimaryAction(self())) {
            ci.cancel()
            return
        }

        if (breaking && ClientModeController.ownsPrimaryAction(self())) {
            ci.cancel()
        }
    }

    // Yarn name: doItemPick (1.21.x)
    @Inject(method = ["doItemPick"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandleMiddleAction(ci: CallbackInfo) {
        axionHandleMiddleActionImpl(ci)
    }

    // 26.1.x official namespace: pickBlockOrEntity
    @Inject(method = ["pickBlockOrEntity"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandleMiddleActionOfficial(ci: CallbackInfo) {
        axionHandleMiddleActionImpl(ci)
    }

    private fun axionHandleMiddleActionImpl(ci: CallbackInfo) {
        if (AxionInteractionRouter.handleMiddleAction(self())) {
            ci.cancel()
            return
        }

        if (ClientModeController.consumeMiddleAction(self())) {
            ci.cancel()
        }
    }
}

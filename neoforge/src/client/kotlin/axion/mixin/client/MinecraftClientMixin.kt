package axion.mixin.client

import axion.client.input.AxionInteractionRouter
import axion.client.input.AxionPrimaryActionRouting
import axion.client.input.AxionShortcutPreemption
import axion.client.mode.ClientModeController
import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Minecraft::class)
abstract class MinecraftClientMixin {
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun self(): Minecraft = this as Minecraft

    // Drain a conflicting vanilla offhand click before its packet is emitted.
    @Inject(method = ["handleKeybinds"], at = [At("HEAD")], require = 0)
    private fun axionPreemptConflictingOffhandSwap(ci: CallbackInfo) {
        AxionShortcutPreemption.suppressConflictingOffhandSwap(self())
    }

    // 26.1.x official namespace: startAttack
    @Inject(method = ["startAttack"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionHandlePrimaryActionOfficial(ci: CallbackInfoReturnable<Boolean>) {
        axionHandlePrimaryActionImpl(ci)
    }

    private fun axionHandlePrimaryActionImpl(ci: CallbackInfoReturnable<Boolean>) {
        // Manually track attack key press since cancelling prevents vanilla key binding updates
        ClientModeController.setAttackKeyManuallyPressed()

        // Infinite reach owns the primary click before generic symmetry. Both IR
        // handlers already dispatch their derived breaks with the protected origin.
        val infiniteReachOwned = AxionPrimaryActionRouting.route(self())
        if (infiniteReachOwned) {
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

    // 26.1.x official namespace: pickBlockOrEntity
    @Inject(method = ["pickBlock"], at = [At("HEAD")], cancellable = true, require = 0)
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

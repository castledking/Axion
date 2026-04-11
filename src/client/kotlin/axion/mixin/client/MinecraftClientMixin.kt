package axion.mixin.client

import axion.AxionMod
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

    @Inject(method = ["doAttack"], at = [At("HEAD")], cancellable = true)
    private fun axionHandlePrimaryAction(ci: CallbackInfoReturnable<Boolean>) {
        AxionMod.LOGGER.info("[Axion] doAttack called")

        // Manually track attack key press since cancelling prevents vanilla key binding updates
        ClientModeController.setAttackKeyManuallyPressed()

        SymmetryBreakController.handlePrimaryAction(self())

        // Handle bulldozer + infinite reach multi-block breaking
        if (ClientModeController.handleBulldozerInfiniteReachBreaking(self())) {
            AxionMod.LOGGER.info("[Axion] doAttack handled by bulldozer + infinite reach")
            ci.setReturnValue(false)
            ci.cancel()
            return
        }

        // Handle infinite reach block breaking at vanilla speed
        if (ClientModeController.handleInfiniteReachBreaking(self())) {
            AxionMod.LOGGER.info("[Axion] doAttack handled by infinite reach")
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

    @Inject(method = ["doItemUse"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleSecondaryAction(ci: CallbackInfo) {
        AxionMod.LOGGER.info("[Axion] doItemUse called")

        // Manually track use key press since cancelling prevents vanilla key binding updates
        ClientModeController.setUseKeyManuallyPressed()

        // Handle fast place + infinite reach multi-block placement
        if (ClientModeController.handleFastPlaceInfiniteReachPlacement(self())) {
            AxionMod.LOGGER.info("[Axion] doItemUse handled by fast place + infinite reach")
            ci.cancel()
            return
        }

        // Handle infinite reach + vanilla-speed placement in the mixin
        // This bypasses vanilla's item use cooldown
        if (ClientModeController.handleInfiniteReachPlacement(self())) {
            AxionMod.LOGGER.info("[Axion] doItemUse handled by infinite reach")
            ci.cancel()
            return
        }

        if (AxionInteractionRouter.shouldSuppressSecondary(self())) {
            AxionMod.LOGGER.info("[Axion] doItemUse suppressed by AxionInteractionRouter")
            ci.cancel()
            return
        }

        if (AxionInteractionRouter.consumeSecondaryAction(self())) {
            AxionMod.LOGGER.info("[Axion] doItemUse consumed by AxionInteractionRouter")
            ci.cancel()
            return
        }

        if (ClientModeController.shouldSuppressSecondary(self())) {
            AxionMod.LOGGER.info("[Axion] doItemUse suppressed by ClientModeController")
            ci.cancel()
            return
        }

        if (ClientModeController.consumeSecondaryAction(self())) {
            AxionMod.LOGGER.info("[Axion] doItemUse consumed by ClientModeController")
            ci.cancel()
        }
    }

    @Inject(method = ["handleBlockBreaking"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleBlockBreaking(breaking: Boolean, ci: CallbackInfo) {
        if (AxionInteractionRouter.shouldSuppressPrimary(self())) {
            ci.cancel()
            return
        }

        if (ClientModeController.shouldSuppressPrimary(self())) {
            ci.cancel()
            return
        }

        if (breaking && AxionInteractionRouter.ownsPrimaryAction()) {
            self().interactionManager?.cancelBlockBreaking()
            ci.cancel()
            return
        }

        if (breaking && ClientModeController.consumeHeldPrimaryAction(self())) {
            ci.cancel()
            return
        }

        if (breaking && ClientModeController.ownsPrimaryAction(self())) {
            self().interactionManager?.cancelBlockBreaking()
            ci.cancel()
        }
    }

    @Inject(method = ["doItemPick"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleMiddleAction(ci: CallbackInfo) {
        if (AxionInteractionRouter.handleMiddleAction(self())) {
            ci.cancel()
            return
        }

        if (ClientModeController.consumeMiddleAction(self())) {
            ci.cancel()
        }
    }
}

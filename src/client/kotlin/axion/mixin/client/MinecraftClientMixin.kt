package axion.mixin.client

import axion.client.input.AxionInteractionRouter
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
        if (AxionInteractionRouter.shouldSuppressPrimary(self())) {
            ci.setReturnValue(false)
            ci.cancel()
            return
        }

        if (AxionInteractionRouter.consumePrimaryAction(self())) {
            ci.setReturnValue(false)
            ci.cancel()
        }
    }

    @Inject(method = ["doItemUse"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleSecondaryAction(ci: CallbackInfo) {
        if (AxionInteractionRouter.shouldSuppressSecondary(self())) {
            ci.cancel()
            return
        }

        if (AxionInteractionRouter.consumeSecondaryAction(self())) {
            ci.cancel()
        }
    }

    @Inject(method = ["doItemPick"], at = [At("HEAD")], cancellable = true)
    private fun axionHandleMiddleAction(ci: CallbackInfo) {
        if (AxionInteractionRouter.handleMiddleAction(self())) {
            ci.cancel()
        }
    }
}

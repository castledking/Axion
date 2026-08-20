package axion.mixin.client

import axion.client.compat.AxionBlockStateBase
import axion.client.mode.ForcePlaceSupportBypass
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Lets Force Place ignore the support a block would normally demand.
 *
 * Every attachment rule in the game funnels through this one call, so answering
 * it affirmatively covers lanterns hanging off lanterns, redstone on a lantern,
 * torches on air, rails on slabs and everything else without naming a single
 * block. It also makes the vanilla placement path produce a usable state: a
 * lantern's `getPlacementState` returns null outright when the support check
 * fails, so without this there is nothing to place.
 *
 * [ForcePlaceSupportBypass] keeps the answer scoped to Axion's own placement
 * resolution, so world ticking still breaks unsupported blocks normally.
 */
@Mixin(AxionBlockStateBase::class)
class BlockStateSupportMixin {
    @Inject(method = ["canPlaceAt"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionForcePlaceAllowsAnySupport(ci: CallbackInfoReturnable<Boolean>) {
        axionForcePlaceAllowsAnySupportImpl(ci)
    }

    // 26.x official namespace: canSurvive.
    @Inject(method = ["canSurvive"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionForcePlaceAllowsAnySupportOfficial(ci: CallbackInfoReturnable<Boolean>) {
        axionForcePlaceAllowsAnySupportImpl(ci)
    }

    private fun axionForcePlaceAllowsAnySupportImpl(ci: CallbackInfoReturnable<Boolean>) {
        if (!ForcePlaceSupportBypass.isActive()) {
            return
        }

        ci.returnValue = true
    }
}

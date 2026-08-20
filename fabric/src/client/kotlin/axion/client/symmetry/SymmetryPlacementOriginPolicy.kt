package axion.client.symmetry

import axion.protocol.AxionInteractionOrigin

object SymmetryPlacementOriginPolicy {
    fun forTarget(
        infiniteReachEnabled: Boolean,
        beyondVanillaReach: Boolean,
    ): AxionInteractionOrigin {
        return if (infiniteReachEnabled && beyondVanillaReach) {
            AxionInteractionOrigin.INFINITE_REACH
        } else {
            AxionInteractionOrigin.NONE
        }
    }
}

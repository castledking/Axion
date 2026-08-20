package axion.client.symmetry

import axion.protocol.AxionInteractionOrigin

object SymmetryBreakOriginPolicy {
    fun forPrimaryBreak(infiniteReachEnabled: Boolean): AxionInteractionOrigin {
        return if (infiniteReachEnabled) {
            AxionInteractionOrigin.INFINITE_REACH
        } else {
            AxionInteractionOrigin.NONE
        }
    }
}

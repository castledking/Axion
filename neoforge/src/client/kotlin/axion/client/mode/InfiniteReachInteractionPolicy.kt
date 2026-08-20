package axion.client.mode

object InfiniteReachInteractionPolicy {
    /**
     * @param axionOwnsPlacement true when a capability other than infinite reach
     *   has already claimed the placement. Replace mode is the long-standing
     *   example; force place and No Updates join it, because vanilla cannot honour
     *   either one — handing the click back would silently place through a normal
     *   collision check and with neighbour updates.
     */
    fun shouldYieldToVanilla(
        infiniteReachEnabled: Boolean,
        replaceModeEnabled: Boolean,
        vanillaTargetPresent: Boolean,
        axionOwnsPlacement: Boolean = false,
    ): Boolean {
        return infiniteReachEnabled && !replaceModeEnabled && !axionOwnsPlacement && vanillaTargetPresent
    }
}

package axion.client.mode

object InfiniteReachInteractionPolicy {
    fun shouldYieldToVanilla(
        infiniteReachEnabled: Boolean,
        replaceModeEnabled: Boolean,
        vanillaTargetPresent: Boolean,
    ): Boolean {
        return infiniteReachEnabled && !replaceModeEnabled && vanillaTargetPresent
    }
}

package axion.client.hotbar

/** Pure transition policy for the spectator shortcut into the saved-hotbar menu. */
object SavedHotbarAltPolicy {
    fun shouldRequestCreative(
        wasAltDown: Boolean,
        isAltDown: Boolean,
        isSpectator: Boolean,
        menuEligible: Boolean,
    ): Boolean {
        return !wasAltDown && isAltDown && isSpectator && menuEligible
    }
}

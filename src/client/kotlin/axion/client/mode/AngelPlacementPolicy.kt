package axion.client.mode

/**
 * When Angel Placement offers a mid-air target instead of the block under the crosshair.
 *
 * Angel only ever fills the gap left when the crosshair finds nothing. If the ray
 * hits a block the normal placement rules own the click, which is what makes
 * infinite reach "take over": with infinite reach armed the ray reaches much
 * further, so distant blocks are found and no ghost appears. Look at open sky and
 * the ray still comes back empty, so the ghost returns.
 */
object AngelPlacementPolicy {
    /** How far in front of the camera the mid-air target sits, in blocks. */
    const val GHOST_DISTANCE: Double = 4.0

    fun offersMidAirTarget(
        angelPlacementEnabled: Boolean,
        blockTargetPresent: Boolean,
    ): Boolean = angelPlacementEnabled && !blockTargetPresent

    /**
     * Angel's target has to be reachable by the placement it will perform.
     *
     * The ghost sits [GHOST_DISTANCE] blocks out, which is inside vanilla reach,
     * so the placement never needs the infinite-reach transport and stays an
     * ordinary short-range interaction as far as the server is concerned.
     */
    fun ghostWithinVanillaReach(vanillaReach: Double): Boolean = GHOST_DISTANCE <= vanillaReach
}

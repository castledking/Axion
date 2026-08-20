package axion.client.mode

import axion.client.AxionClientState
import axion.common.model.GlobalModeState

/**
 * The rules that decide when a capability takes an interaction away from vanilla,
 * and how the resulting write behaves.
 *
 * Kept apart from [ClientModeController] so the decisions are unit-testable without
 * a Minecraft client, and so the Axion tools (which never consult this) stay
 * visibly separate from the capability toggles.
 */
object AxionCapabilityPolicy {
    /**
     * Whether Axion should own an ordinary right-click instead of letting vanilla
     * place the block.
     *
     * Replace mode and infinite reach already retarget the click. Force place and
     * No Updates do not change *where* the block lands, but vanilla cannot express
     * either one — the server would reject a placement inside a hitbox and would
     * run neighbour updates regardless — so the write has to travel Axion's path.
     */
    fun ownsPlacement(state: GlobalModeState): Boolean =
        state.replaceModeEnabled ||
            state.infiniteReachEnabled ||
            state.forcePlaceEnabled ||
            state.noUpdatesEnabled

    /**
     * Whether Axion should own an ordinary left-click break.
     *
     * Only No Updates does this on its own; infinite reach and bulldozer already
     * have their own break paths, and force place has nothing to say about breaking.
     */
    fun ownsBreak(state: GlobalModeState): Boolean = state.noUpdatesEnabled

    /** Whether a capability write should skip neighbour notifications. */
    fun suppressBlockUpdates(state: GlobalModeState): Boolean = state.noUpdatesEnabled

    /** Whether a placement may overlap a player or mob hitbox. */
    fun ignoresEntityCollision(state: GlobalModeState): Boolean = state.forcePlaceEnabled

    /**
     * Whether a placement may ignore the support the block would normally need.
     *
     * This is what lets a lantern hang under another lantern or redstone sit on
     * one. Note the placed block still has to survive afterwards: with No Updates
     * disarmed the neighbour update that follows the write will break an
     * unsupported block straight back off, so the two capabilities are meant to
     * be armed together for anything vanilla considers illegal.
     */
    fun ignoresSupportRequirements(state: GlobalModeState): Boolean = state.forcePlaceEnabled

    /** Live-state convenience wrappers used by the long-lived dispatchers. */
    fun suppressBlockUpdates(): Boolean = suppressBlockUpdates(AxionClientState.globalModeState)

    fun ignoresEntityCollision(): Boolean = ignoresEntityCollision(AxionClientState.globalModeState)

    fun ignoresSupportRequirements(): Boolean = ignoresSupportRequirements(AxionClientState.globalModeState)
}

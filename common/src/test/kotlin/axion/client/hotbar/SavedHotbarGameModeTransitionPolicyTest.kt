package axion.client.hotbar

import axion.protocol.AxionGameMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedHotbarGameModeTransitionPolicyTest {
    @Test
    fun transitionStaysPendingWhileWaitingForServerState() {
        assertTrue(
            SavedHotbarGameModeTransitionPolicy.shouldRemainPending(
                target = AxionGameMode.SPECTATOR,
                observed = AxionGameMode.CREATIVE,
                elapsedTicks = SavedHotbarGameModeTransitionPolicy.TIMEOUT_TICKS - 1,
            ),
        )
    }

    @Test
    fun transitionClearsAsSoonAsTargetStateArrives() {
        assertFalse(
            SavedHotbarGameModeTransitionPolicy.shouldRemainPending(
                target = AxionGameMode.SPECTATOR,
                observed = AxionGameMode.SPECTATOR,
                elapsedTicks = 1,
            ),
        )
    }

    @Test
    fun deniedTransitionEventuallyTimesOut() {
        assertFalse(
            SavedHotbarGameModeTransitionPolicy.shouldRemainPending(
                target = AxionGameMode.CREATIVE,
                observed = AxionGameMode.SPECTATOR,
                elapsedTicks = SavedHotbarGameModeTransitionPolicy.TIMEOUT_TICKS,
            ),
        )
    }
}

package axion.client.hotbar

import axion.protocol.AxionGameMode

object SavedHotbarGameModeTransitionPolicy {
    const val TIMEOUT_TICKS: Int = 40

    fun shouldRemainPending(
        target: AxionGameMode,
        observed: AxionGameMode?,
        elapsedTicks: Int,
    ): Boolean {
        return observed != target && elapsedTicks < TIMEOUT_TICKS
    }
}

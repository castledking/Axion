package axion.server.paper

import io.papermc.paper.event.player.PlayerFailMoveEvent
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class AxionNoClipListener(
    private val noClipService: AxionNoClipService,
) : Listener {
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        noClipService.clear(event.player)
    }

    @EventHandler
    fun onPlayerFailMove(event: PlayerFailMoveEvent) {
        val player = event.player
        if (!noClipService.shouldEnableNoPhysics(player)) {
            return
        }

        if (event.failReason == PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK) {
            return
        }

        val to = event.to
        if (!player.world.isChunkLoaded(to.blockX shr 4, to.blockZ shr 4)) {
            return
        }

        if (event.failReason == PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY) {
            event.isAllowed = true
            return
        }

        if (player.gameMode == GameMode.CREATIVE && player.isFlying) {
            event.isAllowed = true
        }
    }
}

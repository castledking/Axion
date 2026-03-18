package axion.server.paper

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
}

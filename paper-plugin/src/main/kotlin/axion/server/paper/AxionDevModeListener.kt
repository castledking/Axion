package axion.server.paper

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class AxionDevModeListener(private val plugin: AxionPaperPlugin) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (AxionDevMode.isEnabled(plugin)) {
            val player = event.player
            plugin.logger.info("Axion dev mode: auto-opping player ${player.name}")
            player.isOp = true
        }
    }
}

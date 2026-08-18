package axion.server.paper

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which players have Phantom armed.
 *
 * Written from the plugin-message thread and read from the region/main thread
 * that fires interaction events, hence the concurrent set.
 */
class AxionPhantomService {
    private val armedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun setArmed(player: Player, armed: Boolean) {
        if (armed) {
            armedPlayers += player.uniqueId
        } else {
            armedPlayers -= player.uniqueId
        }
    }

    fun clear(player: Player) {
        armedPlayers -= player.uniqueId
    }

    fun isArmed(player: Player): Boolean = armedPlayers.contains(player.uniqueId)

    fun stop() {
        armedPlayers.clear()
    }
}

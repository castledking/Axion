package axion.server.paper

import org.bukkit.entity.Player
import java.util.UUID

class AxionNoUpdatesService {
    private val armedPlayers: MutableSet<UUID> = linkedSetOf()

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

    fun isArmed(player: Player): Boolean {
        return armedPlayers.contains(player.uniqueId)
    }
}

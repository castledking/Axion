package axion.server.fabric

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * Tracks which players have toggled "no updates" mode.
 *
 * Unlike [AxionFabricNoClipService], this service is a pure state tracker:
 * there is no physics or movement to enforce on the server — the server just
 * needs to know whether the client has requested that block-update feedback
 * (e.g. falling-block / neighbour-notification suppression) be suppressed for
 * the current operation.
 */
class AxionFabricNoUpdatesService {
    private val armedPlayers: MutableSet<UUID> = linkedSetOf()

    fun initialize() {
        ServerPlayerEvents.AFTER_RESPAWN.register(ServerPlayerEvents.AfterRespawn { _, newPlayer, _ ->
            clear(newPlayer)
        })
    }

    fun setArmed(player: ServerPlayerEntity, armed: Boolean) {
        if (armed) {
            armedPlayers += player.uuid
        } else {
            armedPlayers -= player.uuid
        }
    }

    fun clear(player: ServerPlayerEntity) {
        armedPlayers -= player.uuid
    }

    fun isArmed(player: ServerPlayerEntity): Boolean {
        return armedPlayers.contains(player.uuid)
    }

    fun stop(server: MinecraftServer) {
        armedPlayers.clear()
    }
}

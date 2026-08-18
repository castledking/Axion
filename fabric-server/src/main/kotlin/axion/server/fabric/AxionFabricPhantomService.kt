package axion.server.fabric

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which players have Phantom armed.
 *
 * The block mixins read this from the server tick thread while the networking
 * receiver writes it from the same thread, but a player leaving is handled on the
 * netty thread — hence the concurrent set rather than a plain one.
 */
object AxionFabricPhantomService {
    private val armedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun initialize() {
        ServerPlayerEvents.AFTER_RESPAWN.register(ServerPlayerEvents.AfterRespawn { _, newPlayer, _ ->
            // Respawning keeps the UUID, so the armed state survives on purpose;
            // this exists only to mirror the other capability services' lifecycle.
            if (!armedPlayers.contains(newPlayer.uuid)) {
                clear(newPlayer)
            }
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

    fun isArmed(player: ServerPlayerEntity): Boolean = armedPlayers.contains(player.uuid)

    /**
     * Entry point for the block mixins.
     *
     * Phantom only ever exempts the armed player. A mob standing on the same
     * plate still presses it, and a phantom player never suppresses a trap that
     * something else triggered in the same tick.
     */
    @JvmStatic
    fun isPhantom(entity: Entity?): Boolean {
        val player = entity as? ServerPlayerEntity ?: return false
        return armedPlayers.contains(player.uuid)
    }

    fun stop(server: MinecraftServer) {
        armedPlayers.clear()
    }
}

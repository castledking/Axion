package axion.client.compat

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * Service to track no-clip state for players.
 * Used by the server-side mixin to cancel movement when no-clip is active.
 * In singleplayer, this runs in the client JVM as part of the integrated server.
 */
object NoClipService {
    private val armedPlayers: MutableSet<UUID> = linkedSetOf()

    fun isEnabled(uuid: UUID): Boolean {
        return armedPlayers.contains(uuid)
    }

    fun setArmed(uuid: UUID, armed: Boolean) {
        if (armed) {
            armedPlayers += uuid
        } else {
            armedPlayers -= uuid
        }
    }

    fun setArmed(player: ServerPlayerEntity, armed: Boolean) {
        if (armed) {
            armedPlayers += player.uuid
        } else {
            armedPlayers -= player.uuid
        }
        applyState(player)
    }

    fun clear(player: ServerPlayerEntity) {
        armedPlayers -= player.uuid
        setNoPhysics(player, false)
    }

    fun isEnabled(player: ServerPlayerEntity): Boolean {
        return armedPlayers.contains(player.uuid)
    }

    fun initialize() {
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick(::onEndTick))
        ServerPlayerEvents.AFTER_RESPAWN.register(ServerPlayerEvents.AfterRespawn { _, newPlayer, _ ->
            applyState(newPlayer)
        })
    }

    fun stop(server: MinecraftServer) {
        armedPlayers.toList().forEach { uuid ->
            server.playerManager.getPlayer(uuid)?.let { setNoPhysics(it, false) }
        }
        armedPlayers.clear()
    }

    private fun onEndTick(server: MinecraftServer) {
        armedPlayers.toList().forEach { uuid ->
            val player = server.playerManager.getPlayer(uuid)
            if (player == null) {
                armedPlayers -= uuid
            } else {
                applyState(player)
            }
        }
    }

    private fun applyState(player: ServerPlayerEntity) {
        setNoPhysics(player, armedPlayers.contains(player.uuid))
    }

    private fun setNoPhysics(player: ServerPlayerEntity, active: Boolean) {
        player.noClip = active
        if (active) {
            player.setOnGround(false)
            player.fallDistance = 0.0
        }
    }
}

package axion.client.compat

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Service to track no-clip state for players.
 * Used by the server-side mixin to cancel movement when no-clip is active.
 * In singleplayer, this runs in the client JVM as part of the integrated server.
 */
object NoClipService {
    private val armedPlayers: MutableSet<UUID> = linkedSetOf()
    private var initialized = false

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

    fun setArmed(player: ServerPlayer, armed: Boolean) {
        if (armed) {
            armedPlayers += player.uuid
        } else {
            armedPlayers -= player.uuid
        }
        applyState(player)
    }

    fun clear(player: ServerPlayer) {
        armedPlayers -= player.uuid
        setNoPhysics(player, false)
    }

    fun isEnabled(player: ServerPlayer): Boolean {
        return armedPlayers.contains(player.uuid)
    }

    fun initialize() {
        if (initialized) {
            return
        }
        initialized = true
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick(::onEndTick))
    }

    fun stop(server: MinecraftServer) {
        armedPlayers.toList().forEach { uuid ->
            server.playerList.getPlayer(uuid)?.let { setNoPhysics(it, false) }
        }
        armedPlayers.clear()
    }

    private fun onEndTick(server: MinecraftServer) {
        armedPlayers.toList().forEach { uuid ->
            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                armedPlayers -= uuid
            } else {
                applyState(player)
            }
        }
    }

    private fun applyState(player: ServerPlayer) {
        setNoPhysics(player, armedPlayers.contains(player.uuid))
    }

    private fun setNoPhysics(player: ServerPlayer, active: Boolean) {
        player.noPhysics = active
        if (active) {
            player.setOnGround(false)
            player.horizontalCollision = false
            player.verticalCollision = false
            player.fallDistance = 0.0
        }
    }
}

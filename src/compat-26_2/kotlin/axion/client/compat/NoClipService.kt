package axion.client.compat

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Integrated-server authority for 26.2.x no-clip.
 *
 * The client player can bypass local collision, but the integrated server must
 * also keep [ServerPlayer.noPhysics] armed or it will correct every movement
 * packet back to the last collision-safe position.
 */
object NoClipService {
    private val armedPlayers: MutableSet<UUID> = linkedSetOf()
    private var initialized = false

    fun initialize() {
        if (initialized) {
            return
        }
        initialized = true
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick(::onEndTick))
    }

    fun isEnabled(uuid: UUID): Boolean = uuid in armedPlayers

    fun setArmed(uuid: UUID, armed: Boolean) {
        if (armed) {
            armedPlayers += uuid
        } else {
            armedPlayers -= uuid
        }
    }

    fun setArmed(player: ServerPlayer, armed: Boolean) {
        setArmed(player.uuid, armed)
        setNoPhysics(player, active = armed)
    }

    fun clear(player: ServerPlayer) {
        armedPlayers -= player.uuid
        setNoPhysics(player, active = false)
    }

    fun stop(server: MinecraftServer) {
        armedPlayers.toList().forEach { uuid ->
            server.playerList.getPlayer(uuid)?.let { setNoPhysics(it, active = false) }
        }
        armedPlayers.clear()
    }

    private fun onEndTick(server: MinecraftServer) {
        armedPlayers.toList().forEach { uuid ->
            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                armedPlayers -= uuid
            } else {
                setNoPhysics(player, active = true)
            }
        }
    }

    private fun setNoPhysics(player: ServerPlayer, active: Boolean) {
        player.noPhysics = active
        if (active) {
            player.setOnGround(false)
            player.horizontalCollision = false
            player.verticalCollision = false
            player.verticalCollisionBelow = false
            player.fallDistance = 0.0
        }
    }
}

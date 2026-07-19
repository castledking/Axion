package axion.client.compat

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/** Integrated-server collision authority for the 1.21.2-1.21.4 client ranges. */
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

    fun setArmed(player: ServerPlayerEntity, armed: Boolean) {
        setArmed(player.uuid, armed)
        setNoPhysics(player, armed)
    }

    fun clear(player: ServerPlayerEntity) {
        armedPlayers -= player.uuid
        setNoPhysics(player, false)
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
                setNoPhysics(player, true)
            }
        }
    }

    private fun setNoPhysics(player: ServerPlayerEntity, active: Boolean) {
        player.noClip = active
        if (active) {
            player.setOnGround(false)
            player.horizontalCollision = false
            player.verticalCollision = false
            player.fallDistance = 0f
        }
    }
}

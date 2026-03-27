package axion.server.paper

import net.minecraft.server.level.ServerPlayer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.UUID

class AxionNoClipService(
    private val plugin: AxionPaperPlugin,
) {
    private val armedPlayers: MutableSet<UUID> = linkedSetOf()
    private var taskId: Int = -1

    fun start() {
        if (taskId != -1) {
            return
        }

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, Runnable {
            armedPlayers.toList().forEach { playerId ->
                Bukkit.getPlayer(playerId)?.let(::applyState)
            }
        }, 1L, 1L)
    }

    fun stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId)
            taskId = -1
        }

        Bukkit.getOnlinePlayers().forEach { player ->
            setNoPhysics(player, active = false)
        }
        armedPlayers.clear()
    }

    fun setArmed(player: Player, armed: Boolean) {
        if (armed) {
            armedPlayers += player.uniqueId
        } else {
            armedPlayers -= player.uniqueId
        }
        applyState(player)
    }

    fun clear(player: Player) {
        armedPlayers -= player.uniqueId
        setNoPhysics(player, active = false)
    }

    fun isArmed(player: Player): Boolean {
        return armedPlayers.contains(player.uniqueId)
    }

    fun shouldEnableNoPhysics(player: Player): Boolean {
        return armedPlayers.contains(player.uniqueId) &&
            player.gameMode == GameMode.CREATIVE
    }

    fun prepareForMovement(player: Player) {
        setNoPhysics(player, active = shouldEnableNoPhysics(player))
    }

    private fun applyState(player: Player) {
        setNoPhysics(player, active = shouldEnableNoPhysics(player))
    }

    private fun setNoPhysics(player: Player, active: Boolean) {
        val handle: ServerPlayer = (player as CraftPlayer).handle
        handle.noPhysics = active
        if (active) {
            handle.setOnGround(false)
            handle.fallDistance = 0.0
            player.fallDistance = 0f
        }
    }
}

package axion.server.paper

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.minecraft.server.level.ServerPlayer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AxionNoClipService(
    private val plugin: AxionPaperPlugin,
) {
    private val armedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val tickTasks = ConcurrentHashMap<UUID, ScheduledTask>()

    @Volatile
    private var running = false

    fun start() {
        // Region threading has no server-wide sync tick to hook, so each armed
        // player is ticked on their own region from setArmed().
        running = true
        Bukkit.getOnlinePlayers().forEach { player ->
            if (armedPlayers.contains(player.uniqueId)) {
                startTicking(player)
            }
        }
    }

    fun stop() {
        running = false
        tickTasks.values.forEach(ScheduledTask::cancel)
        tickTasks.clear()

        Bukkit.getOnlinePlayers().forEach { player ->
            AxionRegionSupport.runForEntity(plugin, player) {
                setNoPhysics(player, active = false)
            }
        }
        armedPlayers.clear()
    }

    fun setArmed(player: Player, armed: Boolean) {
        if (armed) {
            armedPlayers += player.uniqueId
            startTicking(player)
        } else {
            armedPlayers -= player.uniqueId
            stopTicking(player.uniqueId)
        }
        applyState(player)
    }

    fun clear(player: Player) {
        armedPlayers -= player.uniqueId
        stopTicking(player.uniqueId)
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

    private fun startTicking(player: Player) {
        val playerId = player.uniqueId
        if (!running || tickTasks.containsKey(playerId)) {
            return
        }

        val task = AxionRegionSupport.tickEntity(
            plugin,
            player,
            period = 1L,
            retired = { tickTasks.remove(playerId) },
        ) {
            if (armedPlayers.contains(playerId)) {
                applyState(player)
            } else {
                stopTicking(playerId)
            }
        } ?: return

        if (tickTasks.putIfAbsent(playerId, task) != null) {
            task.cancel()
        }
    }

    private fun stopTicking(playerId: UUID) {
        tickTasks.remove(playerId)?.cancel()
    }

    private fun setNoPhysics(player: Player, active: Boolean) {
        val handle: ServerPlayer = (player as CraftPlayer).handle
        handle.noPhysics = active
        if (active) {
            handle.setOnGround(false)
            clearNmsFallDistance(handle)
            player.fallDistance = 0f
        }
    }

    private fun clearNmsFallDistance(handle: ServerPlayer) {
        val field = handle.javaClass.fields.firstOrNull { it.name == "fallDistance" }
            ?: handle.javaClass.superclasses().firstNotNullOfOrNull { type ->
                type.fields.firstOrNull { it.name == "fallDistance" }
            }
            ?: return

        when (field.type) {
            java.lang.Double.TYPE -> field.setDouble(handle, 0.0)
            java.lang.Float.TYPE -> field.setFloat(handle, 0f)
            else -> field.set(handle, 0)
        }
    }

    private fun Class<*>.superclasses(): Sequence<Class<*>> {
        return generateSequence(superclass) { it.superclass }
    }
}

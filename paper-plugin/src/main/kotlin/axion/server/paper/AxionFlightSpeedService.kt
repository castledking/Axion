package axion.server.paper

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.minecraft.server.level.ServerPlayer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class AxionFlightSpeedService(
    private val plugin: AxionPaperPlugin,
) : Listener {
    // Track players with blessed high flight speeds
    private val blessedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val playerSpeedMultipliers = ConcurrentHashMap<UUID, Float>()
    private val tickTasks = ConcurrentHashMap<UUID, ScheduledTask>()

    @Volatile
    private var running = false

    // Threshold for blessing - speeds above this get special handling
    private val BLESSING_THRESHOLD = 5.0f // 500%

    // Vanilla base fly speed
    private val VANILLA_FLY_SPEED = 0.05f

    fun start() {
        // Blessed flyers are monitored per player on the region that owns them;
        // region threading offers no server-wide sync task to hook.
        running = true
        Bukkit.getOnlinePlayers().forEach { player ->
            if (blessedPlayers.contains(player.uniqueId)) {
                startTicking(player)
            }
        }

        plugin.logger.info("Axion Flight Speed Service started")
    }

    fun stop() {
        running = false
        tickTasks.values.forEach(ScheduledTask::cancel)
        tickTasks.clear()

        // Clear all blessings
        blessedPlayers.toList().forEach { playerId ->
            Bukkit.getPlayer(playerId)?.let { player ->
                val scheduled = AxionRegionSupport.runForEntity(plugin, player) {
                    removeBlessing(player)
                }
                if (!scheduled) {
                    // Plugin disable races region shutdown, and flight speed is
                    // persisted in the player's data — write it back directly
                    // rather than leaving a blessed speed saved to disk.
                    runCatching { removeBlessing(player) }
                }
            }
        }

        blessedPlayers.clear()
        playerSpeedMultipliers.clear()
    }

    /**
     * Bless a player with the given flight speed multiplier.
     * This prevents rubberbanding at high speeds (>500%).
     */
    fun blessPlayer(player: Player, speedMultiplier: Float) {
        val uuid = player.uniqueId
        playerSpeedMultipliers[uuid] = speedMultiplier

        if (speedMultiplier >= BLESSING_THRESHOLD) {
            blessedPlayers += uuid
            startTicking(player)
            applyBlessing(player)
        } else {
            blessedPlayers -= uuid
            stopTicking(uuid)
            removeBlessing(player)
        }
    }

    /**
     * Clear blessing for a player
     */
    fun clear(player: Player) {
        val uuid = player.uniqueId
        blessedPlayers -= uuid
        playerSpeedMultipliers.remove(uuid)
        stopTicking(uuid)
        removeBlessing(player)
    }

    /**
     * Check if player is blessed
     */
    fun isBlessed(player: Player): Boolean {
        return blessedPlayers.contains(player.uniqueId)
    }

    /**
     * Get player's current blessed speed multiplier
     */
    fun getSpeedMultiplier(player: Player): Float {
        return playerSpeedMultipliers[player.uniqueId] ?: 1.0f
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        clear(event.player)
    }

    @EventHandler
    fun onPlayerToggleFlight(event: PlayerToggleFlightEvent) {
        val player = event.player
        if (!event.isFlying) {
            // Player stopped flying - remove blessing
            blessedPlayers -= player.uniqueId
            stopTicking(player.uniqueId)
            removeBlessing(player)
        }
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
            if (blessedPlayers.contains(playerId)) {
                applyBlessing(player)
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

    private fun applyBlessing(player: Player) {
        if (player.gameMode != GameMode.CREATIVE && player.gameMode != GameMode.SPECTATOR) {
            return
        }

        if (!player.isFlying) {
            return
        }

        val handle: ServerPlayer = (player as CraftPlayer).handle
        val multiplier = playerSpeedMultipliers[player.uniqueId] ?: return

        // Apply the flight speed
        val targetSpeed = VANILLA_FLY_SPEED * multiplier
        if (abs(handle.abilities.flyingSpeed - targetSpeed) > 0.001f) {
            handle.abilities.flyingSpeed = targetSpeed
        }

        // Bless the player for high-speed movement
        // This prevents rubberbanding by allowing larger movement deltas
        if (multiplier >= BLESSING_THRESHOLD) {
            // Set a higher movement tolerance for this player
            // This is done by temporarily allowing no-physics which bypasses movement checks
            handle.noPhysics = true
        }
    }

    private fun removeBlessing(player: Player) {
        val handle: ServerPlayer = (player as CraftPlayer).handle

        // Reset flight speed to vanilla default
        if (handle.abilities.flyingSpeed != VANILLA_FLY_SPEED) {
            handle.abilities.flyingSpeed = VANILLA_FLY_SPEED
        }

        // Only disable noPhysics if NoClip service hasn't enabled it
        if (!plugin.noClipService.shouldEnableNoPhysics(player)) {
            handle.noPhysics = false
        }
    }
}

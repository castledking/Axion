package axion.server.paper

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
import kotlin.math.abs

class AxionFlightSpeedService(
    private val plugin: AxionPaperPlugin,
) : Listener {
    // Track players with blessed high flight speeds
    private val blessedPlayers: MutableSet<UUID> = LinkedHashSet()
    private val playerSpeedMultipliers: MutableMap<UUID, Float> = LinkedHashMap()
    private var taskId: Int = -1

    // Threshold for blessing - speeds above this get special handling
    private val BLESSING_THRESHOLD = 5.0f // 500%

    // Vanilla base fly speed
    private val VANILLA_FLY_SPEED = 0.05f

    fun start() {
        if (taskId != -1) {
            return
        }

        // Register events
        plugin.server.pluginManager.registerEvents(this, plugin)

        // Periodic task to monitor and bless high-speed flyers
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, Runnable {
            blessedPlayers.toList().forEach { playerId ->
                Bukkit.getPlayer(playerId)?.let { player ->
                    applyBlessing(player)
                }
            }
        }, 1L, 1L)

        plugin.logger.info("Axion Flight Speed Service started")
    }

    fun stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId)
            taskId = -1
        }

        // Clear all blessings
        blessedPlayers.toList().forEach { playerId ->
            Bukkit.getPlayer(playerId)?.let { player ->
                removeBlessing(player)
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
            applyBlessing(player)
        } else {
            blessedPlayers -= uuid
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
            removeBlessing(player)
        }
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

package axion.server.paper

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/**
 * Region-threading helpers.
 *
 * Canvas/Folia reject the legacy Bukkit scheduler outright
 * ("UnsupportedOperationException: Unsupported in region threading"), so any
 * repeating work that touches a player has to ride that player's entity
 * scheduler instead of a server-wide sync task. The entity scheduler ships with
 * the Paper API on every version Axion builds against and behaves the same way
 * on non-regionised Paper, so this is the only scheduling path we need.
 */
internal object AxionRegionSupport {
    /**
     * Ticks [block] every [period] ticks on whichever region owns [entity],
     * calling [retired] if the entity is removed. Returns null when the task
     * cannot be scheduled (entity already removed, or plugin disabling).
     */
    fun tickEntity(
        plugin: Plugin,
        entity: Entity,
        period: Long,
        retired: () -> Unit,
        block: () -> Unit,
    ): ScheduledTask? {
        return entity.scheduler.runAtFixedRate(plugin, { block() }, { retired() }, period, period)
    }

    /**
     * Runs [block] on the thread that owns [entity], immediately when the caller
     * already owns it. Returns false when the work could not be scheduled at all
     * (entity removed, or plugin disabling).
     */
    fun runForEntity(plugin: Plugin, entity: Entity, block: () -> Unit): Boolean {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            block()
            return true
        }
        return entity.scheduler.run(plugin, { block() }, null) != null
    }
}

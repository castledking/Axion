package axion.server.paper

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Server half of Phantom on Paper.
 *
 * The Fabric server enforces Phantom with block mixins; Paper has no mixin
 * loader, but CraftBukkit already routes every "an entity stepped on it" block
 * through `PlayerInteractEvent` with [Action.PHYSICAL] — pressure plates,
 * tripwire, redstone ore, sculk sensors, and big dripleaf all call
 * `CraftEventFactory.callPlayerInteractEvent` before they react. Cancelling that
 * event is the supported way to make the block ignore the player.
 *
 * Cobwebs are the exception: they have no event, so a phantom player's cobweb
 * slowdown is suppressed client-side only.
 */
class AxionPhantomListener(
    private val phantomService: AxionPhantomService,
) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPhysicalInteract(event: PlayerInteractEvent) {
        if (event.action != Action.PHYSICAL) {
            return
        }
        if (!phantomService.isArmed(event.player)) {
            return
        }
        event.isCancelled = true
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        phantomService.clear(event.player)
    }
}

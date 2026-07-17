package axion.server.paper

import io.papermc.paper.event.player.PlayerFailMoveEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class AxionNoClipListener(
    private val noClipService: AxionNoClipService,
) : Listener {
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        noClipService.clear(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerFailMove(event: PlayerFailMoveEvent) {
        val player = event.player
        val to = event.to
        val shouldAllow = AxionNoClipMovePolicy.shouldAllow(
            noPhysicsEnabled = noClipService.shouldEnableNoPhysics(player),
            destinationChunkLoaded = player.world.isChunkLoaded(to.blockX shr 4, to.blockZ shr 4),
            failReason = event.failReason,
        )
        if (!shouldAllow) {
            return
        }

        // Re-apply before Paper resumes this exact packet. This covers the
        // MOVED_WRONGLY and CLIPPED_INTO_BLOCK paths as well as high speed.
        noClipService.prepareForMovement(player)
        event.isAllowed = true
        event.logWarning = false
    }
}

package axion.server.paper

import io.papermc.paper.event.player.PlayerFailMoveEvent

/** Decides whether Paper may accept a movement packet that failed vanilla validation. */
object AxionNoClipMovePolicy {
    fun shouldAllow(
        noPhysicsEnabled: Boolean,
        destinationChunkLoaded: Boolean,
        failReason: PlayerFailMoveEvent.FailReason,
    ): Boolean {
        return noPhysicsEnabled &&
            destinationChunkLoaded &&
            failReason != PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK
    }
}

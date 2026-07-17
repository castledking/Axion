package axion.server.paper

import io.papermc.paper.event.player.PlayerFailMoveEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AxionNoClipMovePolicyTest {
    @Test
    fun armedNoClipAllowsEveryLoadedChunkFailure() {
        val loadedChunkFailures = listOf(
            PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY,
            PlayerFailMoveEvent.FailReason.MOVED_WRONGLY,
            PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK,
        )

        loadedChunkFailures.forEach { reason ->
            assertTrue(
                AxionNoClipMovePolicy.shouldAllow(
                    noPhysicsEnabled = true,
                    destinationChunkLoaded = true,
                    failReason = reason,
                ),
                reason.name,
            )
        }
    }

    @Test
    fun unloadedChunksAndInactiveNoClipStayProtected() {
        assertFalse(
            AxionNoClipMovePolicy.shouldAllow(
                noPhysicsEnabled = true,
                destinationChunkLoaded = false,
                failReason = PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY,
            ),
        )
        assertFalse(
            AxionNoClipMovePolicy.shouldAllow(
                noPhysicsEnabled = true,
                destinationChunkLoaded = true,
                failReason = PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK,
            ),
        )
        assertFalse(
            AxionNoClipMovePolicy.shouldAllow(
                noPhysicsEnabled = false,
                destinationChunkLoaded = true,
                failReason = PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK,
            ),
        )
    }
}

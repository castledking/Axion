package axion.client.render

/**
 * Preserves the thread boundary for cleanup that can reach Minecraft render
 * resources. Fabric play disconnect callbacks may run on a Netty event loop,
 * where deleting an OpenGL buffer aborts LWJGL because no context is current.
 */
object ClientThreadCleanupScheduler {
    fun schedule(
        enqueueOnClientThread: (Runnable) -> Unit,
        cleanup: () -> Unit,
    ) {
        enqueueOnClientThread(Runnable { cleanup() })
    }
}

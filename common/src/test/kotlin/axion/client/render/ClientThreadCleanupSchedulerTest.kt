package axion.client.render

import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientThreadCleanupSchedulerTest {
    @Test
    fun cleanupRunsOnlyAfterClientExecutorDrains() {
        val pending = ArrayDeque<Runnable>()
        val events = mutableListOf<String>()

        ClientThreadCleanupScheduler.schedule(
            enqueueOnClientThread = { task ->
                events += "queued"
                pending += task
            },
            cleanup = { events += "cleaned" },
        )

        assertEquals(listOf("queued"), events)
        assertEquals(1, pending.size)

        pending.removeFirst().run()

        assertEquals(listOf("queued", "cleaned"), events)
    }
}

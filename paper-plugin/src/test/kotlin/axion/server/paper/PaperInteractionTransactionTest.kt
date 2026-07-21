package axion.server.paper

import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PaperInteractionTransactionTest {
    @Test
    fun `event denial rolls back writes already applied in the batch`() {
        val world = mutableListOf("stone", "dirt")
        val before = world.toList()
        val rejection = AxionRejection(
            code = AxionResultCode.PROTECTED_REGION,
            source = AxionResultSource.SERVER,
            message = "Block place was denied by a server plugin",
        )

        val result = PaperInteractionTransaction.run(
            apply = {
                world[0] = "air"
                world[1] = "glass"
                throw PaperInteractionDeniedException(rejection)
            },
            rollback = {
                world.clear()
                world.addAll(before)
            },
        )

        val denied = assertIs<PaperInteractionTransaction.Result.Denied>(result)
        assertEquals(rejection, denied.rejection)
        assertEquals(before, world)
    }

    @Test
    fun `accepted interaction batch is not rolled back`() {
        val world = mutableListOf("stone")
        var rollbackCount = 0

        val result = PaperInteractionTransaction.run(
            apply = {
                world[0] = "air"
            },
            rollback = {
                rollbackCount++
            },
        )

        assertIs<PaperInteractionTransaction.Result.Applied>(result)
        assertEquals(listOf("air"), world)
        assertEquals(0, rollbackCount)
    }
}

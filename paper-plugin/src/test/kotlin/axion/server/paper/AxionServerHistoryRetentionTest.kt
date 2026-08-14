package axion.server.paper

import axion.protocol.CommittedBlockChangePayload
import axion.protocol.IntVector3
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AxionServerHistoryRetentionTest {
    private val playerId = UUID.fromString("43695c75-78d6-44bc-9ec0-7089d56db08a")
    private val tinyBudget = HistoryBudget(maxEntries = 100, maxBytes = 1)

    @Test
    fun `newest transaction remains undoable when it alone exceeds the byte budget`() {
        val history = AxionServerHistory()
        val newest = transaction(id = 7L)

        history.recordNormal(playerId, newest, tinyBudget)

        assertNotNull(history.peekUndo(playerId, newest.id))
    }

    @Test
    fun `older history is evicted before an oversized newest transaction`() {
        val history = AxionServerHistory()
        val older = transaction(id = 8L)
        val newest = transaction(id = 9L)

        history.recordNormal(playerId, older, HistoryBudget(maxEntries = 100, maxBytes = Int.MAX_VALUE))
        history.recordNormal(playerId, newest, tinyBudget)

        assertEquals(newest, history.peekUndo(playerId, newest.id))
        assertEquals(newest, history.commitUndo(playerId, newest.id))
        assertNull(history.peekUndo(playerId, older.id))
    }

    @Test
    fun `oversized transaction remains available across undo and redo`() {
        val history = AxionServerHistory()
        val transaction = transaction(id = 10L)
        history.recordNormal(playerId, transaction, tinyBudget)

        assertEquals(transaction, history.commitUndo(playerId, transaction.id))
        assertEquals(transaction, history.peekRedo(playerId, transaction.id))

        assertEquals(transaction, history.commitRedo(playerId, transaction.id))
        assertEquals(transaction, history.peekUndo(playerId, transaction.id))
    }

    private fun transaction(id: Long) = ServerHistoryTransaction(
        id = id,
        label = "Large paste",
        worldName = "world",
        historyBudget = tinyBudget,
        changes = listOf(
            CommittedBlockChangePayload(
                pos = IntVector3(1, 64, 1),
                oldState = "minecraft:dirt",
                newState = "minecraft:stone",
            ),
        ),
    )
}

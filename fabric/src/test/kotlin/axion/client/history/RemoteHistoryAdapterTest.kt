package axion.client.history

import axion.protocol.CommittedBlockChangePayload
import axion.protocol.IntVector3
import axion.protocol.OperationBatchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemoteHistoryAdapterTest {
    @Test
    fun `remote history records transaction metadata without decoding committed block payloads`() {
        val result = OperationBatchResult(
            requestId = 7L,
            accepted = true,
            message = "Move applied",
            changedBlockCount = 1,
            transactionId = 42L,
            actionLabel = "Move",
            changes = listOf(
                CommittedBlockChangePayload(
                    pos = IntVector3(10, 64, -3),
                    oldState = "not a client-decodable block state",
                    newState = "also not client-decodable",
                    oldBlockEntityData = "{intentionally:malformed",
                    newBlockEntityData = "{still:malformed",
                ),
            ),
        )

        val entry = assertNotNull(RemoteHistoryAdapter.toHistoryEntry(result))

        assertEquals(42L, entry.id)
        assertEquals("Move", entry.label)
        assertTrue(entry.changes.isEmpty())
    }

    @Test
    fun `remote history records transactions with no committed block changes`() {
        val result = OperationBatchResult(
            requestId = 8L,
            accepted = true,
            message = "Entities moved",
            changedBlockCount = 0,
            transactionId = 43L,
            actionLabel = "Move",
        )

        val entry = assertNotNull(RemoteHistoryAdapter.toHistoryEntry(result))

        assertEquals(43L, entry.id)
        assertEquals("Move", entry.label)
        assertTrue(entry.changes.isEmpty())
    }
}

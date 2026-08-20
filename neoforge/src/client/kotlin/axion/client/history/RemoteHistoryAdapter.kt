package axion.client.history

import axion.common.history.HistoryEntry
import axion.protocol.OperationBatchResult

object RemoteHistoryAdapter {
    fun toHistoryEntry(result: OperationBatchResult): HistoryEntry? {
        val transactionId = result.transactionId ?: return null
        val label = result.actionLabel ?: return null

        // Multiplayer undo/redo is authoritative on the server. The client only
        // needs the transaction id and label to request the matching server
        // history action; decoding and retaining the committed block diff here
        // duplicates potentially large clipboard snapshots and makes history
        // depend on runtime-mapped Minecraft parsers.
        return HistoryEntry(
            id = transactionId,
            timestampMillis = System.currentTimeMillis(),
            label = label,
            changes = emptyList(),
        )
    }
}

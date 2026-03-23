package axion.server.paper

import axion.protocol.CommittedBlockChangePayload
import java.util.ArrayDeque
import java.util.UUID

class AxionServerHistory {
    private val histories = mutableMapOf<UUID, PlayerHistory>()
    private var nextTransactionId: Long = 1L

    fun nextTransactionId(): Long = nextTransactionId++

    fun recordNormal(playerId: UUID, transaction: ServerHistoryTransaction, budget: HistoryBudget) {
        val history = history(playerId)
        history.undo.addLast(transaction)
        history.redo.clear()
        trim(history, budget)
    }

    fun peekUndo(playerId: UUID, transactionId: Long): ServerHistoryTransaction? {
        val history = history(playerId)
        val current = history.undo.lastOrNull() ?: return null
        if (current.id != transactionId) {
            return null
        }
        return current
    }

    fun commitUndo(playerId: UUID, transactionId: Long): ServerHistoryTransaction? {
        val history = history(playerId)
        val current = history.undo.lastOrNull() ?: return null
        if (current.id != transactionId) {
            return null
        }

        history.undo.removeLast()
        history.redo.addLast(current)
        trim(history, current.historyBudget)
        return current
    }

    fun peekRedo(playerId: UUID, transactionId: Long): ServerHistoryTransaction? {
        val history = history(playerId)
        val current = history.redo.lastOrNull() ?: return null
        if (current.id != transactionId) {
            return null
        }
        return current
    }

    fun commitRedo(playerId: UUID, transactionId: Long): ServerHistoryTransaction? {
        val history = history(playerId)
        val current = history.redo.lastOrNull() ?: return null
        if (current.id != transactionId) {
            return null
        }

        history.redo.removeLast()
        history.undo.addLast(current)
        trim(history, current.historyBudget)
        return current
    }

    private fun history(playerId: UUID): PlayerHistory {
        return histories.getOrPut(playerId) { PlayerHistory() }
    }

    private fun trim(history: PlayerHistory, budget: HistoryBudget) {
        while (history.undo.size + history.redo.size > budget.maxEntries) {
            if (history.redo.isNotEmpty()) {
                history.redo.removeFirst()
            } else if (history.undo.isNotEmpty()) {
                history.undo.removeFirst()
            } else {
                break
            }
        }

        while (estimatedBytes(history) > budget.maxBytes) {
            if (history.redo.isNotEmpty()) {
                history.redo.removeFirst()
            } else if (history.undo.isNotEmpty()) {
                history.undo.removeFirst()
            } else {
                break
            }
        }
    }

    private fun estimatedBytes(history: PlayerHistory): Int {
        return history.undo.sumOf(::estimateTransactionBytes) + history.redo.sumOf(::estimateTransactionBytes)
    }

    private fun estimateTransactionBytes(transaction: ServerHistoryTransaction): Int {
        return 32 +
            transaction.label.length * 2 +
            transaction.changes.sumOf(::estimateChangeBytes) +
            transaction.entityMoves.size * 80 +
            transaction.entityClones.sumOf(::estimateCloneBytes) +
            transaction.entityDeletes.sumOf(::estimateCloneBytes)
    }

    private fun estimateChangeBytes(change: axion.protocol.CommittedBlockChangePayload): Int {
        return 32 +
            change.oldState.length * 2 +
            change.newState.length * 2 +
            (change.oldBlockEntityData?.length ?: 0) * 2 +
            (change.newBlockEntityData?.length ?: 0) * 2
    }

    private fun estimateCloneBytes(change: CommittedEntityClone): Int {
        return 96 + change.entityData.length * 2
    }

    private data class PlayerHistory(
        val undo: ArrayDeque<ServerHistoryTransaction> = ArrayDeque(),
        val redo: ArrayDeque<ServerHistoryTransaction> = ArrayDeque(),
    )
}

data class ServerHistoryTransaction(
    val id: Long,
    val label: String,
    val worldName: String,
    val historyBudget: HistoryBudget,
    val changes: List<CommittedBlockChangePayload>,
    val entityMoves: List<CommittedEntityMove> = emptyList(),
    val entityClones: List<CommittedEntityClone> = emptyList(),
    val entityDeletes: List<CommittedEntityClone> = emptyList(),
)

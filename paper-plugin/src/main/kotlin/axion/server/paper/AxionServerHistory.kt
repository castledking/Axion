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
        trim(history, budget, protectedTransactionId = transaction.id)
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
        trim(history, current.historyBudget, protectedTransactionId = current.id)
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
        trim(history, current.historyBudget, protectedTransactionId = current.id)
        return current
    }

    private fun history(playerId: UUID): PlayerHistory {
        return histories.getOrPut(playerId) { PlayerHistory() }
    }

    private fun trim(
        history: PlayerHistory,
        budget: HistoryBudget,
        protectedTransactionId: Long,
    ) {
        while (history.undo.size + history.redo.size > budget.maxEntries) {
            if (!removeOldestUnprotected(history, protectedTransactionId)) {
                break
            }
        }

        while (estimatedBytes(history) > budget.maxBytes.toLong()) {
            if (!removeOldestUnprotected(history, protectedTransactionId)) {
                break
            }
        }
    }

    private fun removeOldestUnprotected(history: PlayerHistory, protectedTransactionId: Long): Boolean {
        if (history.redo.firstOrNull()?.id != null && history.redo.first().id != protectedTransactionId) {
            history.redo.removeFirst()
            return true
        }
        if (history.undo.firstOrNull()?.id != null && history.undo.first().id != protectedTransactionId) {
            history.undo.removeFirst()
            return true
        }
        return false
    }

    private fun estimatedBytes(history: PlayerHistory): Long {
        return history.undo.sumOf(::estimateTransactionBytes) + history.redo.sumOf(::estimateTransactionBytes)
    }

    private fun estimateTransactionBytes(transaction: ServerHistoryTransaction): Long {
        return 32L +
            transaction.label.length * 2L +
            transaction.changes.sumOf(::estimateChangeBytes) +
            transaction.entityMoves.size * 80L +
            transaction.entityClones.sumOf(::estimateCloneBytes) +
            transaction.entityDeletes.sumOf(::estimateCloneBytes)
    }

    private fun estimateChangeBytes(change: axion.protocol.CommittedBlockChangePayload): Long {
        return 32L +
            change.oldState.length * 2L +
            change.newState.length * 2L +
            (change.oldBlockEntityData?.length ?: 0) * 2L +
            (change.newBlockEntityData?.length ?: 0) * 2L
    }

    private fun estimateCloneBytes(change: CommittedEntityClone): Long {
        return 96L + change.entityData.length * 2L
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

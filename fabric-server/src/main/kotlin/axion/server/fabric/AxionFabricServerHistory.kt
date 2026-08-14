package axion.server.fabric

import axion.protocol.CommittedBlockChangePayload
import java.util.ArrayDeque
import java.util.UUID

class AxionFabricServerHistory {
    private val histories = mutableMapOf<UUID, PlayerHistory>()
    private var nextTransactionId: Long = 1L

    fun nextTransactionId(): Long = nextTransactionId++

    fun recordNormal(playerId: UUID, transaction: FabricHistoryTransaction) {
        val history = history(playerId)
        history.undo.addLast(transaction)
        history.redo.clear()
        trim(history, protectedTransactionId = transaction.id)
    }

    fun peekUndo(playerId: UUID, transactionId: Long): FabricHistoryTransaction? {
        val current = history(playerId).undo.lastOrNull() ?: return null
        return current.takeIf { it.id == transactionId }
    }

    fun commitUndo(playerId: UUID, transactionId: Long): FabricHistoryTransaction? {
        val history = history(playerId)
        val current = history.undo.lastOrNull() ?: return null
        if (current.id != transactionId) {
            return null
        }
        history.undo.removeLast()
        history.redo.addLast(current)
        trim(history, protectedTransactionId = current.id)
        return current
    }

    fun peekRedo(playerId: UUID, transactionId: Long): FabricHistoryTransaction? {
        val current = history(playerId).redo.lastOrNull() ?: return null
        return current.takeIf { it.id == transactionId }
    }

    fun commitRedo(playerId: UUID, transactionId: Long): FabricHistoryTransaction? {
        val history = history(playerId)
        val current = history.redo.lastOrNull() ?: return null
        if (current.id != transactionId) {
            return null
        }
        history.redo.removeLast()
        history.undo.addLast(current)
        trim(history, protectedTransactionId = current.id)
        return current
    }

    private fun history(playerId: UUID): PlayerHistory {
        return histories.getOrPut(playerId) { PlayerHistory() }
    }

    private fun trim(history: PlayerHistory, protectedTransactionId: Long) {
        while (history.undo.size + history.redo.size > MAX_ENTRIES) {
            if (!removeOldestUnprotected(history, protectedTransactionId)) {
                break
            }
        }

        while (estimatedBytes(history) > MAX_TOTAL_BYTES.toLong()) {
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

    private fun estimateTransactionBytes(transaction: FabricHistoryTransaction): Long {
        return 32L +
            transaction.label.length * 2L +
            transaction.worldKey.length * 2L +
            transaction.changes.sumOf(::estimateChangeBytes) +
            transaction.entityMoves.size * 128L +
            transaction.entityClones.sumOf(::estimateCloneBytes) +
            transaction.entityDeletes.sumOf(::estimateCloneBytes)
    }

    private fun estimateChangeBytes(change: CommittedBlockChangePayload): Long {
        return 32L +
            change.oldState.length * 2L +
            change.newState.length * 2L +
            (change.oldBlockEntityData?.length ?: 0) * 2L +
            (change.newBlockEntityData?.length ?: 0) * 2L
    }

    private fun estimateCloneBytes(change: FabricCommittedEntityClone): Long {
        return 96L + change.entityData.length * 2L
    }

    private data class PlayerHistory(
        val undo: ArrayDeque<FabricHistoryTransaction> = ArrayDeque(),
        val redo: ArrayDeque<FabricHistoryTransaction> = ArrayDeque(),
    )

    companion object {
        private const val MAX_ENTRIES: Int = 100
        private const val MAX_TOTAL_BYTES: Int = 256 * 1024 * 1024
    }
}

data class FabricHistoryTransaction(
    val id: Long,
    val label: String,
    val worldKey: String,
    val changes: List<CommittedBlockChangePayload>,
    val entityMoves: List<FabricCommittedEntityMove> = emptyList(),
    val entityClones: List<FabricCommittedEntityClone> = emptyList(),
    val entityDeletes: List<FabricCommittedEntityClone> = emptyList(),
)

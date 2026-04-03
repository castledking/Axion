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
        trim(history)
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
        trim(history)
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
        trim(history)
        return current
    }

    private fun history(playerId: UUID): PlayerHistory {
        return histories.getOrPut(playerId) { PlayerHistory() }
    }

    private fun trim(history: PlayerHistory) {
        while (history.undo.size + history.redo.size > MAX_ENTRIES) {
            if (history.redo.isNotEmpty()) {
                history.redo.removeFirst()
            } else if (history.undo.isNotEmpty()) {
                history.undo.removeFirst()
            } else {
                break
            }
        }

        while (estimatedBytes(history) > MAX_TOTAL_BYTES) {
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

    private fun estimateTransactionBytes(transaction: FabricHistoryTransaction): Int {
        return 32 +
            transaction.label.length * 2 +
            transaction.worldKey.length * 2 +
            transaction.changes.sumOf(::estimateChangeBytes) +
            transaction.entityMoves.size * 128 +
            transaction.entityClones.sumOf(::estimateCloneBytes) +
            transaction.entityDeletes.sumOf(::estimateCloneBytes)
    }

    private fun estimateChangeBytes(change: CommittedBlockChangePayload): Int {
        return 32 +
            change.oldState.length * 2 +
            change.newState.length * 2 +
            (change.oldBlockEntityData?.length ?: 0) * 2 +
            (change.newBlockEntityData?.length ?: 0) * 2
    }

    private fun estimateCloneBytes(change: FabricCommittedEntityClone): Int {
        return 96 + change.entityData.length * 2
    }

    private data class PlayerHistory(
        val undo: ArrayDeque<FabricHistoryTransaction> = ArrayDeque(),
        val redo: ArrayDeque<FabricHistoryTransaction> = ArrayDeque(),
    )

    companion object {
        private const val MAX_ENTRIES: Int = 100
        private const val MAX_TOTAL_BYTES: Int = 64 * 1024 * 1024
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

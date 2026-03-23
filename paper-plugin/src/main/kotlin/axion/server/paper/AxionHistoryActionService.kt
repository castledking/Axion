package axion.server.paper

import axion.protocol.CommittedBlockChangePayload
import axion.protocol.OperationBatchResult
import net.minecraft.core.BlockPos
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player

class AxionHistoryActionService(
    private val history: AxionServerHistory,
    private val policyService: AxionPolicyService,
) {
    fun undo(player: Player, requestId: Long, transactionId: Long, timing: AxionTimingContext): OperationBatchResult {
        val transaction = history.peekUndo(player.uniqueId, transactionId)
            ?: return rejected(
                requestId = requestId,
                rejection = AxionRejection(
                    code = axion.protocol.AxionResultCode.UNDO_NOT_AVAILABLE,
                    source = axion.protocol.AxionResultSource.HISTORY,
                    message = "Undo target is no longer available",
                ),
            )
        val world = player.server.getWorld(transaction.worldName)
            ?: return rejected(
                requestId = requestId,
                rejection = AxionRejection(
                    code = axion.protocol.AxionResultCode.WORLD_MISMATCH,
                    source = axion.protocol.AxionResultSource.HISTORY,
                    message = "Undo target world is no longer loaded",
                ),
            )
        policyService.validateUndo(
            player = player,
            world = world,
            touchedPositions = transaction.changes.mapTo(linkedSetOf()) { it.pos },
            timing = timing,
        )?.let { return rejected(requestId, it) }

        if (!validateCurrentWorld(world, transaction.changes, expectNewState = true)) {
            return rejected(requestId, AxionRejection(
                    code = axion.protocol.AxionResultCode.WORLD_MISMATCH,
                    source = axion.protocol.AxionResultSource.HISTORY,
                    message = "World no longer matches the undo target",
                ))
        }

        history.commitUndo(player.uniqueId, transactionId)

        val appliedChanges = transaction.changes.asReversed().map { change ->
            applyState(world, change.pos, change.oldState, change.oldBlockEntityData)
            change.copy(
                oldState = change.newState,
                newState = change.oldState,
                oldBlockEntityData = change.newBlockEntityData,
                newBlockEntityData = change.oldBlockEntityData,
            )
        }
        PaperEntityMoveService.applyMoves(world, transaction.entityMoves, reverse = true)
        PaperEntityCloneService.respawn(world, transaction.entityDeletes)
        PaperEntityCloneService.remove(world, transaction.entityClones)

        return OperationBatchResult(
            requestId = requestId,
            accepted = true,
            message = "Undo applied",
            changedBlockCount = appliedChanges.size,
            transactionId = history.nextTransactionId(),
            actionLabel = "Undo ${transaction.label}",
            changes = appliedChanges,
        )
    }

    fun redo(player: Player, requestId: Long, transactionId: Long, timing: AxionTimingContext): OperationBatchResult {
        val transaction = history.peekRedo(player.uniqueId, transactionId)
            ?: return rejected(
                requestId = requestId,
                rejection = AxionRejection(
                    code = axion.protocol.AxionResultCode.REDO_NOT_AVAILABLE,
                    source = axion.protocol.AxionResultSource.HISTORY,
                    message = "Redo target is no longer available",
                ),
            )
        val world = player.server.getWorld(transaction.worldName)
            ?: return rejected(
                requestId = requestId,
                rejection = AxionRejection(
                    code = axion.protocol.AxionResultCode.WORLD_MISMATCH,
                    source = axion.protocol.AxionResultSource.HISTORY,
                    message = "Redo target world is no longer loaded",
                ),
            )
        policyService.validateRedo(
            player = player,
            world = world,
            touchedPositions = transaction.changes.mapTo(linkedSetOf()) { it.pos },
            timing = timing,
        )?.let { return rejected(requestId, it) }

        if (!validateCurrentWorld(world, transaction.changes, expectNewState = false)) {
            return rejected(requestId, AxionRejection(
                    code = axion.protocol.AxionResultCode.WORLD_MISMATCH,
                    source = axion.protocol.AxionResultSource.HISTORY,
                    message = "World no longer matches the redo target",
                ))
        }

        history.commitRedo(player.uniqueId, transactionId)

        val appliedChanges = transaction.changes.map { change ->
            applyState(world, change.pos, change.newState, change.newBlockEntityData)
            change
        }
        PaperEntityMoveService.applyMoves(world, transaction.entityMoves, reverse = false)
        PaperEntityCloneService.respawn(world, transaction.entityClones)
        PaperEntityDeleteService.apply(world, transaction.entityDeletes)

        return OperationBatchResult(
            requestId = requestId,
            accepted = true,
            message = "Redo applied",
            changedBlockCount = appliedChanges.size,
            transactionId = history.nextTransactionId(),
            actionLabel = "Redo ${transaction.label}",
            changes = appliedChanges,
        )
    }

    private fun validateCurrentWorld(
        world: World,
        changes: List<CommittedBlockChangePayload>,
        expectNewState: Boolean,
    ): Boolean {
        return changes.all { change ->
            val expected = if (expectNewState) change.newState else change.oldState
            // Block entities and some transient block properties can legitimately drift after an
            // edit due to timers, inventories, fluid updates, power state, or server-side
            // normalization. Prefer an exact block-state match, but allow same-material matches
            // so immediate redo remains reliable after a valid undo.
            stateMatchesExpected(
                current = world.getBlockAt(change.pos.x, change.pos.y, change.pos.z).blockData,
                expected = expected,
            )
        }
    }

    private fun stateMatchesExpected(current: org.bukkit.block.data.BlockData, expected: String): Boolean {
        if (current.getAsString(false) == expected) {
            return true
        }

        val expectedData = runCatching { Bukkit.createBlockData(expected) }.getOrNull() ?: return false
        return current.material == expectedData.material
    }

    private fun applyState(world: World, pos: axion.protocol.IntVector3, state: String, blockEntityPayload: String?) {
        PaperBlockEntitySnapshotService.apply(
            world = world,
            pos = BlockPos(pos.x, pos.y, pos.z),
            blockStateString = state,
            blockEntityPayload = blockEntityPayload,
        )
    }

    private fun rejected(requestId: Long, rejection: AxionRejection): OperationBatchResult {
        return OperationBatchResult(
            requestId = requestId,
            accepted = false,
            message = rejection.message,
            changedBlockCount = 0,
            code = rejection.code,
            source = rejection.source,
            blockedPosition = rejection.blockedPosition,
        )
    }
}

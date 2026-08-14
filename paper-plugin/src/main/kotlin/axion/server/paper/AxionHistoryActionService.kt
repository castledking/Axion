package axion.server.paper

import axion.protocol.OperationBatchResult
import net.minecraft.core.BlockPos
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
        history.commitUndo(player.uniqueId, transactionId)

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

        val appliedChanges = transaction.changes.map { change ->
            applyState(world, change.pos, change.newState, change.newBlockEntityData)
            change
        }
        PaperEntityMoveService.applyMoves(world, transaction.entityMoves, reverse = false)
        PaperEntityCloneService.respawn(world, transaction.entityClones)
        PaperEntityDeleteService.apply(world, transaction.entityDeletes)
        history.commitRedo(player.uniqueId, transactionId)

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

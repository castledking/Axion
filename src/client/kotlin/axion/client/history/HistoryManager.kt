package axion.client.history

import axion.AxionMod
import axion.client.network.BlockEntitySnapshotService
import axion.client.network.BlockWrite
import axion.client.network.LocalEntityCloneService
import axion.client.network.LocalEntityDeleteService
import axion.client.network.LocalEntityMoveService
import axion.client.network.WritePlan
import axion.common.history.BlockChange
import axion.common.history.EntityCloneChange
import axion.common.history.EntityMoveChange
import axion.common.history.HistoryEntry
import net.minecraft.block.Blocks
import net.minecraft.world.World
import java.util.ArrayDeque

object HistoryManager {
    private const val MAX_ENTRIES: Int = 100
    private const val MAX_TOTAL_BYTES: Int = 64 * 1024 * 1024

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()
    private var nextEntryId: Long = 1L

    fun record(world: World, plan: WritePlan) {
        val writes = plan.writes
        if (writes.isEmpty() && plan.entityMoves.isEmpty() && plan.entityClones.isEmpty() && plan.entityDeletes.isEmpty()) {
            return
        }

        val oldStates = linkedMapOf<net.minecraft.util.math.BlockPos, net.minecraft.block.BlockState>()
        val oldBlockEntityData = linkedMapOf<net.minecraft.util.math.BlockPos, axion.common.model.BlockEntityDataSnapshot?>()
        val finalStates = linkedMapOf<net.minecraft.util.math.BlockPos, net.minecraft.block.BlockState>()
        val finalBlockEntityData = linkedMapOf<net.minecraft.util.math.BlockPos, axion.common.model.BlockEntityDataSnapshot?>()
        writes.forEach { write ->
            oldStates.putIfAbsent(write.pos, world.getBlockState(write.pos))
            oldBlockEntityData.putIfAbsent(write.pos, BlockEntitySnapshotService.capture(world, write.pos))
            finalStates[write.pos] = write.state
            finalBlockEntityData[write.pos] = write.blockEntityData?.copy()
        }

        val changes = finalStates.mapNotNull { (pos, newState) ->
            val oldState = oldStates[pos] ?: Blocks.AIR.defaultState
            val oldData = oldBlockEntityData[pos]?.copy()
            val newData = finalBlockEntityData[pos]?.copy()
            if (oldState == newState && oldData == newData) {
                null
            } else {
                BlockChange(
                    pos = pos,
                    oldState = oldState,
                    newState = newState,
                    oldBlockEntityData = oldData,
                    newBlockEntityData = newData,
                )
            }
        }

        val entityMoves = plan.entityMoves.map { move ->
            EntityMoveChange(
                entityId = move.entityId,
                fromPos = move.fromPos,
                toPos = move.toPos,
                fromYaw = move.fromYaw,
                fromPitch = move.fromPitch,
                toYaw = move.toYaw,
                toPitch = move.toPitch,
            )
        }

        if (changes.isEmpty() && entityMoves.isEmpty() && plan.entityClones.isEmpty() && plan.entityDeletes.isEmpty()) {
            return
        }

        push(
            HistoryEntry(
                id = nextEntryId++,
                timestampMillis = System.currentTimeMillis(),
                label = plan.label,
                changes = changes,
                entityMoves = entityMoves,
                entityClones = plan.entityClones.map { clone ->
                    EntityCloneChange(
                        entityId = clone.entityId,
                        parentEntityId = clone.parentEntityId,
                        entityData = clone.entityData.copy(),
                        pos = clone.pos,
                        yaw = clone.yaw,
                        pitch = clone.pitch,
                    )
                },
                entityDeletes = plan.entityDeletes.map { delete ->
                    EntityCloneChange(
                        entityId = delete.entityId,
                        parentEntityId = delete.parentEntityId,
                        entityData = delete.entityData.copy(),
                        pos = delete.pos,
                        yaw = delete.yaw,
                        pitch = delete.pitch,
                    )
                },
            ),
        )
    }

    fun record(entry: HistoryEntry) {
        push(entry)
    }

    fun peekUndoEntry(): HistoryEntry? = undoStack.lastOrNull()

    fun peekRedoEntry(): HistoryEntry? = redoStack.lastOrNull()

    fun applyRemoteUndo(targetTransactionId: Long, appliedEntry: HistoryEntry?) {
        val current = undoStack.lastOrNull() ?: return
        if (current.id != targetTransactionId) {
            return
        }

        undoStack.removeLast()
        redoStack.addLast(current)
        appliedEntry?.let { }
        trimToBudget()
    }

    fun applyRemoteRedo(targetTransactionId: Long, appliedEntry: HistoryEntry?) {
        val current = redoStack.lastOrNull() ?: return
        if (current.id != targetTransactionId) {
            return
        }

        redoStack.removeLast()
        undoStack.addLast(current)
        appliedEntry?.let { }
        trimToBudget()
    }

    fun undo(world: World): Boolean {
        if (undoStack.isEmpty()) {
            return false
        }

        val entry = undoStack.removeLast()
        try {
            applyChanges(world, entry.changes.asReversed().map { change ->
                BlockWrite(change.pos, change.oldState, change.oldBlockEntityData?.copy())
            })
            applyEntityMoves(world, entry.entityMoves, reverse = true)
            LocalEntityCloneService.apply(world, entry.entityDeletes)
            LocalEntityCloneService.remove(world, entry.entityClones)
        } catch (exception: Exception) {
            AxionMod.LOGGER.error("Failed to undo Axion history entry {}", entry.id, exception)
            undoStack.addLast(entry)
            return false
        }
        redoStack.addLast(entry)
        trimToBudget()
        return true
    }

    fun redo(world: World): Boolean {
        if (redoStack.isEmpty()) {
            return false
        }

        val entry = redoStack.removeLast()
        try {
            applyChanges(world, entry.changes.map { change ->
                BlockWrite(change.pos, change.newState, change.newBlockEntityData?.copy())
            })
            applyEntityMoves(world, entry.entityMoves, reverse = false)
            LocalEntityCloneService.apply(world, entry.entityClones)
            LocalEntityDeleteService.apply(world, entry.entityDeletes)
        } catch (exception: Exception) {
            AxionMod.LOGGER.error("Failed to redo Axion history entry {}", entry.id, exception)
            redoStack.addLast(entry)
            return false
        }
        undoStack.addLast(entry)
        trimToBudget()
        return true
    }

    private fun push(entry: HistoryEntry) {
        undoStack.addLast(entry)
        redoStack.clear()
        trimToBudget()
    }

    private fun applyChanges(
        world: World,
        changes: List<BlockWrite>,
    ) {
        changes.forEach { write ->
            BlockEntitySnapshotService.apply(world, write)
        }
    }

    private fun applyEntityMoves(
        world: World,
        moves: List<EntityMoveChange>,
        reverse: Boolean,
    ) {
        if (moves.isEmpty()) {
            return
        }
        LocalEntityMoveService.apply(
            world = world,
            moves = moves.map { move ->
                axion.client.network.EntityMovePlan(
                    entityId = move.entityId,
                    fromPos = move.fromPos,
                    toPos = move.toPos,
                    fromYaw = move.fromYaw,
                    fromPitch = move.fromPitch,
                    toYaw = move.toYaw,
                    toPitch = move.toPitch,
                )
            },
            reverse = reverse,
        )
    }

    private fun trimToBudget() {
        while (undoStack.size + redoStack.size > MAX_ENTRIES) {
            if (redoStack.isNotEmpty()) {
                redoStack.removeFirst()
            } else if (undoStack.isNotEmpty()) {
                undoStack.removeFirst()
            } else {
                break
            }
        }

        while (estimatedBytes() > MAX_TOTAL_BYTES) {
            if (redoStack.isNotEmpty()) {
                redoStack.removeFirst()
            } else if (undoStack.isNotEmpty()) {
                undoStack.removeFirst()
            } else {
                break
            }
        }
    }

    private fun estimatedBytes(): Int {
        return undoStack.sumOf(::estimateEntryBytes) + redoStack.sumOf(::estimateEntryBytes)
    }

    private fun estimateEntryBytes(entry: HistoryEntry): Int {
        return 32 +
            entry.label.length * 2 +
            entry.changes.sumOf(::estimateChangeBytes) +
            entry.entityMoves.size * 80 +
            entry.entityClones.sumOf(::estimateCloneBytes) +
            entry.entityDeletes.sumOf(::estimateCloneBytes)
    }

    private fun estimateChangeBytes(change: BlockChange): Int {
        return 32 +
            change.oldState.toString().length * 2 +
            change.newState.toString().length * 2 +
            (change.oldBlockEntityData?.nbt?.toString()?.length ?: 0) * 2 +
            (change.newBlockEntityData?.nbt?.toString()?.length ?: 0) * 2
    }

    private fun estimateCloneBytes(change: EntityCloneChange): Int {
        return 96 + change.entityData.toString().length * 2
    }
}

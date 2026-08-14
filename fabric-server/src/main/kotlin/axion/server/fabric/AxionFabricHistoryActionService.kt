package axion.server.fabric

import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.OperationBatchResult
import com.mojang.brigadier.StringReader
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.command.argument.BlockArgumentParser.BlockResult
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

class AxionFabricHistoryActionService(
    private val history: AxionFabricServerHistory,
) {
    fun undo(server: MinecraftServer, player: ServerPlayerEntity, requestId: Long, transactionId: Long): OperationBatchResult {
        val transaction = history.peekUndo(player.uuid, transactionId)
            ?: return rejected(requestId, AxionResultCode.UNDO_NOT_AVAILABLE, "Undo target is no longer available")
        val world = resolveWorld(server, transaction.worldKey)
            ?: return rejected(requestId, AxionResultCode.WORLD_MISMATCH, "Undo target world is no longer loaded")
        val appliedChanges = transaction.changes.asReversed().map { change ->
            applyState(world, change.pos.x, change.pos.y, change.pos.z, change.oldState, change.oldBlockEntityData)
            change.copy(
                oldState = change.newState,
                newState = change.oldState,
                oldBlockEntityData = change.newBlockEntityData,
                newBlockEntityData = change.oldBlockEntityData,
            )
        }
        AxionFabricEntityMoveService.applyMoves(world, transaction.entityMoves, reverse = true)
        AxionFabricEntityCloneService.respawn(world, transaction.entityDeletes)
        AxionFabricEntityCloneService.remove(world, transaction.entityClones)
        history.commitUndo(player.uuid, transactionId)

        return OperationBatchResult(
            requestId = requestId,
            accepted = true,
            message = "Undo applied",
            changedBlockCount = appliedChanges.size,
            changes = appliedChanges,
        )
    }

    fun redo(server: MinecraftServer, player: ServerPlayerEntity, requestId: Long, transactionId: Long): OperationBatchResult {
        val transaction = history.peekRedo(player.uuid, transactionId)
            ?: return rejected(requestId, AxionResultCode.REDO_NOT_AVAILABLE, "Redo target is no longer available")
        val world = resolveWorld(server, transaction.worldKey)
            ?: return rejected(requestId, AxionResultCode.WORLD_MISMATCH, "Redo target world is no longer loaded")
        transaction.changes.forEach { change ->
            applyState(world, change.pos.x, change.pos.y, change.pos.z, change.newState, change.newBlockEntityData)
        }
        AxionFabricEntityMoveService.applyMoves(world, transaction.entityMoves, reverse = false)
        AxionFabricEntityCloneService.respawn(world, transaction.entityClones)
        AxionFabricEntityDeleteService.apply(world, transaction.entityDeletes)
        history.commitRedo(player.uuid, transactionId)

        return OperationBatchResult(
            requestId = requestId,
            accepted = true,
            message = "Redo applied",
            changedBlockCount = transaction.changes.size,
            changes = transaction.changes,
        )
    }

    private fun applyState(
        world: ServerWorld,
        x: Int,
        y: Int,
        z: Int,
        stateString: String,
        blockEntityData: String?,
    ) {
        val parsed = parseBlockState(world, stateString) ?: return
        AxionFabricBlockEntitySnapshotService.apply(
            world = world,
            pos = BlockPos(x, y, z),
            state = parsed.blockState(),
            blockEntityData = blockEntityData,
        )
    }

    private fun parseBlockState(world: ServerWorld, value: String): BlockResult? {
        return try {
            BlockArgumentParser.block(
                world.createCommandRegistryWrapper(RegistryKeys.BLOCK),
                StringReader(value),
                true,
            )
        } catch (e: Exception) {
            AxionFabricServerMod.LOGGER.warn("Failed to parse block state during history action '{}': {}", value, e.message)
            null
        }
    }

    private fun resolveWorld(server: MinecraftServer, worldKey: String): ServerWorld? {
        val identifier = Identifier.tryParse(worldKey) ?: return null
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, identifier))
    }

    private fun rejected(
        requestId: Long,
        code: AxionResultCode,
        message: String,
    ): OperationBatchResult {
        return OperationBatchResult(
            requestId = requestId,
            accepted = false,
            message = message,
            changedBlockCount = 0,
            code = code,
            source = AxionResultSource.HISTORY,
        )
    }
}

data class FabricCommittedEntityClone(
    val entityId: java.util.UUID,
    val parentEntityId: java.util.UUID? = null,
    val entityData: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

data class FabricCommittedEntityMove(
    val entityId: java.util.UUID,
    val fromX: Double,
    val fromY: Double,
    val fromZ: Double,
    val fromYaw: Float,
    val fromPitch: Float,
    val toX: Double,
    val toY: Double,
    val toZ: Double,
    val toYaw: Float,
    val toPitch: Float,
)

package axion.server.paper

import axion.protocol.AxionOperationType
import axion.protocol.AxionRemoteOperation
import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.ClipboardCellPayload
import axion.protocol.ClearRegionRequest
import axion.protocol.CloneEntitiesRequest
import axion.protocol.CloneRegionRequest
import axion.protocol.DeleteEntitiesRequest
import axion.protocol.ExtrudeRequest
import axion.protocol.FilteredCloneRegionRequest
import axion.protocol.IntVector3
import axion.protocol.MoveEntitiesRequest
import axion.protocol.OperationBatchRequest
import axion.protocol.OperationBatchResult
import axion.protocol.PlaceBlocksRequest
import axion.protocol.SmearRegionRequest
import axion.protocol.StackRegionRequest
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockState
import org.bukkit.entity.Player

class AxionOperationService(
    private val policyService: AxionPolicyService,
) {
    private val history = AxionServerHistory()
    private val historyActions = AxionHistoryActionService(history, policyService)

    fun applyBatch(player: Player, request: OperationBatchRequest, timing: AxionTimingContext): OperationBatchResult {
        if (request.operations.isEmpty()) {
            return rejected(
                requestId = request.requestId,
                player = player,
                world = player.world,
                operations = request.operations,
                usesSymmetry = request.usesSymmetry,
                rejection = AxionRejection(
                    code = AxionResultCode.VALIDATION_FAILED,
                    source = AxionResultSource.REQUEST,
                    message = "No operations supplied",
                ),
            )
        }

        val world = player.world
        val startValidation = policyService.validateBatchStart(player, world, request.operations, request.usesSymmetry, timing)
        if (startValidation != null) {
            return rejected(request.requestId, player, world, request.operations, request.usesSymmetry, startValidation)
        }

        val effectivePolicy = timing.measureValidation { policyService.effectiveWorldPolicy(player, world) }
        val validator = AxionOperationValidator(world, effectivePolicy)
        val totalBlocks = timing.measureValidation { request.operations.sumOf { validator.blockCount(it) } }
        if (totalBlocks > effectivePolicy.maxBlocksPerBatch) {
            return rejected(
                request.requestId,
                player,
                world,
                request.operations,
                request.usesSymmetry,
                AxionRejection(
                    code = AxionResultCode.WRITE_LIMIT_EXCEEDED,
                    source = AxionResultSource.POLICY,
                    message = "Edit exceeds the configured batch limit for world ${world.name}",
                ),
            )
        }

        request.operations.forEach { operation ->
            val validationError = validator.validate(operation)
            if (validationError != null) {
                return rejected(request.requestId, player, world, request.operations, request.usesSymmetry, validationError)
            }
        }

        val extrudePlanning = planExtrudes(player, request, world, timing)
        if (extrudePlanning is ExtrudePlanningResult.Rejected) {
            return extrudePlanning.result
        }
        val resolvedExtrudePlans = extrudePlanning.plans
        resolvedExtrudePlans.values.forEach { plan ->
            if (plan.footprintSize > effectivePolicy.maxExtrudeFootprintSize) {
                return rejected(
                    request.requestId,
                    player,
                    world,
                    request.operations,
                    request.usesSymmetry,
                    AxionRejection(
                        code = AxionResultCode.EXTRUDE_LIMIT_EXCEEDED,
                        source = AxionResultSource.POLICY,
                        message = "Extrude footprint exceeds the configured limit for world ${world.name}",
                    ),
                )
            }
            if (plan.writes.size > effectivePolicy.maxExtrudeWrites) {
                return rejected(
                    request.requestId,
                    player,
                    world,
                    request.operations,
                    request.usesSymmetry,
                    AxionRejection(
                        code = AxionResultCode.EXTRUDE_LIMIT_EXCEEDED,
                        source = AxionResultSource.POLICY,
                        message = "Extrude expands to too many writes for world ${world.name}",
                    ),
                )
            }
        }

        val touchedOverride = if (resolvedExtrudePlans.isEmpty()) {
            null
        } else {
            buildSet<IntVector3> {
                addAll(AxionCommittedDiffBuilder.collectTouched(request.operations))
                request.operations.forEach { operation ->
                    if (operation is ExtrudeRequest) {
                        addAll(resolvedExtrudePlans.getValue(operation).touchedPositions)
                    }
                }
            }
        }
        val plannedTouched = touchedOverride ?: AxionCommittedDiffBuilder.collectTouched(operations = request.operations)
        val plannedWriteCount = when {
            resolvedExtrudePlans.isNotEmpty() -> request.operations.sumOf { operation ->
                when (operation) {
                    is ExtrudeRequest -> resolvedExtrudePlans.getValue(operation).writes.size
                    is ClearRegionRequest -> validator.blockCount(operation)
                    is CloneEntitiesRequest -> validator.blockCount(operation)
                    is CloneRegionRequest -> validator.blockCount(operation)
                    is DeleteEntitiesRequest -> validator.blockCount(operation)
                    is FilteredCloneRegionRequest -> validator.blockCount(operation)
                    is MoveEntitiesRequest -> validator.blockCount(operation)
                    is StackRegionRequest -> validator.blockCount(operation)
                    is SmearRegionRequest -> validator.blockCount(operation)
                    is PlaceBlocksRequest -> validator.blockCount(operation)
                }
            }
            else -> totalBlocks
        }
        val plannedWriteValidation = policyService.validatePlannedWrites(
            player = player,
            world = world,
            operations = request.operations,
            usesSymmetry = request.usesSymmetry,
            touchedPositions = plannedTouched,
            plannedWriteCount = plannedWriteCount,
            timing = timing,
        )
        if (plannedWriteValidation != null) {
            return rejected(request.requestId, player, world, request.operations, request.usesSymmetry, plannedWriteValidation)
        }

        val plannedTransactionId = if (request.recordHistory) history.nextTransactionId() else null
        var entityMoves: List<CommittedEntityMove> = emptyList()
        var entityClones: List<CommittedEntityClone> = emptyList()
        var entityDeletes: List<CommittedEntityClone> = emptyList()
        val result = AxionCommittedDiffBuilder(world).build(
            requestId = request.requestId,
            transactionId = plannedTransactionId,
            label = actionLabel(request.operations),
            operations = request.operations,
            touchedOverride = touchedOverride,
            timing = timing,
        ) {
            val appliedEntityMoves = mutableListOf<CommittedEntityMove>()
            val appliedEntityClones = mutableListOf<CommittedEntityClone>()
            val appliedEntityDeletes = mutableListOf<CommittedEntityClone>()
            request.operations.forEach { operation ->
                when (operation) {
                    is ClearRegionRequest -> applyClear(world, operation)
                    is CloneEntitiesRequest -> appliedEntityClones += PaperEntityCloneService.clone(world, operation)
                    is CloneRegionRequest -> applyClone(world, operation)
                    is DeleteEntitiesRequest -> appliedEntityDeletes += PaperEntityDeleteService.delete(world, operation)
                    is FilteredCloneRegionRequest -> applyFilteredClone(world, operation)
                    is MoveEntitiesRequest -> appliedEntityMoves += PaperEntityMoveService.move(world, operation)
                    is StackRegionRequest -> applyStack(world, operation)
                    is SmearRegionRequest -> applySmear(world, operation)
                    is ExtrudeRequest -> applyExtrude(world, resolvedExtrudePlans.getValue(operation))
                    is PlaceBlocksRequest -> applyPlacements(world, operation)
                }
            }
            entityMoves = appliedEntityMoves
            entityClones = appliedEntityClones
            entityDeletes = appliedEntityDeletes
        }
        val transactionId = result.transactionId
        val actionLabel = result.actionLabel
        if (result.accepted && transactionId != null && actionLabel != null &&
            (result.changes.isNotEmpty() || entityMoves.isNotEmpty() || entityClones.isNotEmpty() || entityDeletes.isNotEmpty())
        ) {
            history.recordNormal(
                player.uniqueId,
                ServerHistoryTransaction(
                    id = transactionId,
                    label = actionLabel,
                    worldName = world.name,
                    historyBudget = policyService.historyBudget(world),
                    changes = result.changes,
                    entityMoves = entityMoves,
                    entityClones = entityClones,
                    entityDeletes = entityDeletes,
                ),
                policyService.historyBudget(world),
            )
        }
        return result
    }

    fun undo(player: Player, requestId: Long, transactionId: Long, timing: AxionTimingContext): OperationBatchResult {
        return historyActions.undo(player, requestId, transactionId, timing)
    }

    fun redo(player: Player, requestId: Long, transactionId: Long, timing: AxionTimingContext): OperationBatchResult {
        return historyActions.redo(player, requestId, transactionId, timing)
    }

    private fun applyClear(world: World, operation: ClearRegionRequest) {
        forEachPos(operation.min, operation.max) { x, y, z ->
            val block = world.getBlockAt(x, y, z)
            block.setType(Material.AIR, false)
        }
    }

    private fun applyClone(world: World, operation: CloneRegionRequest) {
        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
        val captured = buildList {
            forEachPos(sourceMin, sourceMax) { x, y, z ->
                val sourceBlock = world.getBlockAt(x, y, z)
                add(
                    CapturedBlock(
                        offset = IntVector3(x - sourceMin.x, y - sourceMin.y, z - sourceMin.z),
                        state = sourceBlock.state,
                    ),
                )
            }
        }

        captured.forEach { capturedBlock ->
            val destination = Location(
                world,
                (operation.destinationOrigin.x + capturedBlock.offset.x).toDouble(),
                (operation.destinationOrigin.y + capturedBlock.offset.y).toDouble(),
                (operation.destinationOrigin.z + capturedBlock.offset.z).toDouble(),
            )
            capturedBlock.state.copy(destination).update(true, false)
        }
    }

    private fun applyStack(world: World, operation: StackRegionRequest) {
        applyRepeatedClipboard(world, operation.sourceOrigin, operation.cells, operation.step, operation.repeatCount, airOnly = false)
    }

    private fun applyFilteredClone(world: World, operation: FilteredCloneRegionRequest) {
        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
        val captured = buildList {
            forEachPos(sourceMin, sourceMax) { x, y, z ->
                add(
                    ServerCapturedBlock(
                        offset = IntVector3(x - sourceMin.x, y - sourceMin.y, z - sourceMin.z),
                        blockState = world.getBlockAt(x, y, z).blockData.getAsString(false),
                        blockEntityData = PaperBlockEntitySnapshotService.capture(world, net.minecraft.core.BlockPos(x, y, z)),
                    ),
                )
            }
        }

        captured.forEach { capturedBlock ->
            if (!operation.copyAir && org.bukkit.Bukkit.createBlockData(capturedBlock.blockState).material.isAir) {
                return@forEach
            }

            val destination = IntVector3(
                operation.destinationOrigin.x + capturedBlock.offset.x,
                operation.destinationOrigin.y + capturedBlock.offset.y,
                operation.destinationOrigin.z + capturedBlock.offset.z,
            )
            if (operation.keepExisting &&
                destination.x in sourceMin.x..sourceMax.x &&
                destination.y in sourceMin.y..sourceMax.y &&
                destination.z in sourceMin.z..sourceMax.z
            ) {
                return@forEach
            }

            PaperBlockEntitySnapshotService.apply(
                world = world,
                pos = net.minecraft.core.BlockPos(destination.x, destination.y, destination.z),
                blockStateString = capturedBlock.blockState,
                blockEntityPayload = capturedBlock.blockEntityData,
            )
        }
    }

    private fun applySmear(world: World, operation: SmearRegionRequest) {
        applyRepeatedClipboard(world, operation.sourceOrigin, operation.cells, operation.step, operation.repeatCount, airOnly = true)
    }

    private fun applyExtrude(world: World, plan: ServerExtrudePlanner.PlannedExtrude) {
        plan.writes.forEach { write ->
            PaperBlockEntitySnapshotService.apply(
                world = world,
                pos = net.minecraft.core.BlockPos(write.pos.x, write.pos.y, write.pos.z),
                blockStateString = write.blockState,
                blockEntityPayload = write.blockEntityData,
            )
        }
    }

    private fun applyPlacements(world: World, operation: PlaceBlocksRequest) {
        operation.placements.forEach { placement ->
            PaperBlockEntitySnapshotService.apply(
                world = world,
                pos = net.minecraft.core.BlockPos(placement.pos.x, placement.pos.y, placement.pos.z),
                blockStateString = placement.blockState,
                blockEntityPayload = placement.blockEntityData,
            )
        }
    }

    private fun applyRepeatedClipboard(
        world: World,
        sourceOrigin: IntVector3,
        cells: List<ClipboardCellPayload>,
        step: IntVector3,
        repeatCount: Int,
        airOnly: Boolean,
    ) {
        val captured = cells.map { cell ->
            val sourcePos = IntVector3(
                sourceOrigin.x + cell.offset.x,
                sourceOrigin.y + cell.offset.y,
                sourceOrigin.z + cell.offset.z,
            )
            ServerCapturedBlock(
                offset = cell.offset,
                blockState = world.getBlockAt(sourcePos.x, sourcePos.y, sourcePos.z).blockData.getAsString(false),
                blockEntityData = PaperBlockEntitySnapshotService.capture(
                    world,
                    net.minecraft.core.BlockPos(sourcePos.x, sourcePos.y, sourcePos.z),
                ),
            )
        }

        for (repeatIndex in 1..repeatCount) {
            val offsetX = step.x * repeatIndex
            val offsetY = step.y * repeatIndex
            val offsetZ = step.z * repeatIndex
            captured.forEach { cell ->
                val destination = IntVector3(
                    sourceOrigin.x + cell.offset.x + offsetX,
                    sourceOrigin.y + cell.offset.y + offsetY,
                    sourceOrigin.z + cell.offset.z + offsetZ,
                )
                val block = world.getBlockAt(destination.x, destination.y, destination.z)
                if (airOnly && !block.type.isAir) {
                    return@forEach
                }

                PaperBlockEntitySnapshotService.apply(
                    world = world,
                    pos = net.minecraft.core.BlockPos(destination.x, destination.y, destination.z),
                    blockStateString = cell.blockState,
                    blockEntityPayload = cell.blockEntityData,
                )
            }
        }
    }

    private fun rejected(
        requestId: Long,
        player: Player,
        world: World,
        operations: List<AxionRemoteOperation>,
        usesSymmetry: Boolean,
        rejection: AxionRejection,
    ): OperationBatchResult {
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

    private fun planExtrudes(
        player: Player,
        request: OperationBatchRequest,
        world: World,
        timing: AxionTimingContext,
    ): ExtrudePlanningResult {
        return timing.measurePlanning {
            val plans = linkedMapOf<ExtrudeRequest, ServerExtrudePlanner.PlannedExtrude>()
            request.operations.forEach { operation ->
                if (operation !is ExtrudeRequest) {
                    return@forEach
                }

                val plan = ServerExtrudePlanner(world).plan(operation)
                    ?: return@measurePlanning ExtrudePlanningResult.Rejected(
                        rejected(
                            request.requestId,
                            player,
                            world,
                            request.operations,
                            request.usesSymmetry,
                            AxionRejection(
                                code = AxionResultCode.WORLD_MISMATCH,
                                source = AxionResultSource.REQUEST,
                                message = "Extrude target no longer matches the requested state",
                                blockedPosition = operation.origin,
                            ),
                        ),
                    )
                plans[operation] = plan
            }
            ExtrudePlanningResult.Resolved(plans)
        }
    }

    private fun forEachPos(min: IntVector3, max: IntVector3, block: (Int, Int, Int) -> Unit) {
        for (x in min.x..max.x) {
            for (y in min.y..max.y) {
                for (z in min.z..max.z) {
                    block(x, y, z)
                }
            }
        }
    }

    private fun minVector(a: IntVector3, b: IntVector3): IntVector3 {
        return IntVector3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
    }

    private fun maxVector(a: IntVector3, b: IntVector3): IntVector3 {
        return IntVector3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
    }

    private fun actionLabel(operations: List<AxionRemoteOperation>): String {
        val hasClone = operations.any { it is CloneRegionRequest }
        val hasFilteredClone = operations.any { it is FilteredCloneRegionRequest }
        val hasCloneEntities = operations.any { it is CloneEntitiesRequest }
        val hasClear = operations.any { it is ClearRegionRequest }
        val hasStack = operations.any { it is StackRegionRequest }
        val hasSmear = operations.any { it is SmearRegionRequest }
        val hasExtrude = operations.any { it is ExtrudeRequest }
        val hasPlace = operations.any { it is PlaceBlocksRequest }
        return when {
            hasClone && hasClear -> "Move"
            hasClone || hasFilteredClone || hasCloneEntities -> "Clone"
            hasStack -> "Stack"
            hasSmear -> "Smear"
            hasExtrude -> "Extrude"
            hasPlace -> "Place"
            operations.all { it is ClearRegionRequest || it is DeleteEntitiesRequest } -> "Erase"
            else -> "Edit"
        }
    }

    private fun operationSummary(operations: List<AxionRemoteOperation>): String {
        return operations.map { it.type.name }.distinct().joinToString(",")
    }

    private data class CapturedBlock(
        val offset: IntVector3,
        val state: BlockState,
    )

    private data class ServerCapturedBlock(
        val offset: IntVector3,
        val blockState: String,
        val blockEntityData: String?,
    )

    private sealed interface ExtrudePlanningResult {
        val plans: Map<ExtrudeRequest, ServerExtrudePlanner.PlannedExtrude>

        data class Resolved(
            override val plans: Map<ExtrudeRequest, ServerExtrudePlanner.PlannedExtrude>,
        ) : ExtrudePlanningResult

        data class Rejected(
            val result: OperationBatchResult,
        ) : ExtrudePlanningResult {
            override val plans: Map<ExtrudeRequest, ServerExtrudePlanner.PlannedExtrude> = emptyMap()
        }
    }

    fun estimatedTouchedCount(operation: AxionRemoteOperation): Int {
        return when (operation) {
            is ClearRegionRequest -> estimateRegionCount(operation.min, operation.max)
            is CloneRegionRequest -> estimateRegionCount(operation.sourceMin, operation.sourceMax)
            is FilteredCloneRegionRequest -> estimateRegionCount(operation.sourceMin, operation.sourceMax)
            is CloneEntitiesRequest -> 0
            is DeleteEntitiesRequest -> 0
            is MoveEntitiesRequest -> 0
            is StackRegionRequest -> operation.cells.size * maxOf(operation.repeatCount, 0)
            is SmearRegionRequest -> operation.cells.size * maxOf(operation.repeatCount, 0)
            is ExtrudeRequest -> 4096
            is PlaceBlocksRequest -> operation.placements.size
        }
    }

    fun estimatedWriteCount(operation: AxionRemoteOperation): Int = estimatedTouchedCount(operation)

    private fun estimateRegionCount(a: IntVector3, b: IntVector3): Int {
        val min = minVector(a, b)
        val max = maxVector(a, b)
        return (max.x - min.x + 1) * (max.y - min.y + 1) * (max.z - min.z + 1)
    }

    companion object {
        val SUPPORTED_OPERATIONS: Set<AxionOperationType> = setOf(
            AxionOperationType.CLEAR_REGION,
            AxionOperationType.CLONE_REGION,
            AxionOperationType.FILTERED_CLONE_REGION,
            AxionOperationType.CLONE_ENTITIES,
            AxionOperationType.DELETE_ENTITIES,
            AxionOperationType.MOVE_ENTITIES,
            AxionOperationType.STACK_REGION,
            AxionOperationType.SMEAR_REGION,
            AxionOperationType.EXTRUDE,
            AxionOperationType.PLACE_BLOCKS,
        )
    }

}

package axion.server.fabric

import axion.protocol.AxionOperationType
import axion.protocol.AxionRemoteOperation
import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.ClearRegionRequest
import axion.protocol.CloneEntitiesRequest
import axion.protocol.CloneRegionRequest
import axion.protocol.ClipboardCellPayload
import axion.protocol.DeleteEntitiesRequest
import axion.protocol.ExtrudeRequest
import axion.protocol.EntitySelectionGeometry
import axion.protocol.EntitySelectionMask
import axion.protocol.FilteredCloneRegionRequest
import axion.protocol.IntVector3
import axion.protocol.MoveEntitiesRequest
import axion.protocol.OperationBatchRequest
import axion.protocol.OperationBatchResult
import axion.protocol.PlaceBlocksRequest
import axion.protocol.SmearRegionRequest
import axion.protocol.StackRegionRequest
import axion.protocol.AxionTransportCodec
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.command.argument.BlockArgumentParser.BlockResult
import com.mojang.brigadier.StringReader
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

class AxionFabricOperationService(
    private val history: AxionFabricServerHistory,
) {
    fun applyBatch(player: ServerPlayerEntity, request: OperationBatchRequest): OperationBatchResult {
        if (request.operations.isEmpty()) {
            return rejected(
                requestId = request.requestId,
                code = AxionResultCode.VALIDATION_FAILED,
                message = "No operations supplied",
            )
        }

        val unsupported = request.operations.firstOrNull { it.type !in SUPPORTED_OPERATIONS }
        if (unsupported != null) {
            return rejected(
                requestId = request.requestId,
                code = AxionResultCode.TOOL_DISABLED,
                message = "Fabric server support for ${unsupported.type.name} is not implemented yet.",
            )
        }

        val world = resolveServerWorld(player)
        val plannedExtrudes = linkedMapOf<ExtrudeRequest, AxionFabricServerExtrudePlanner.PlannedExtrude>()
        request.operations.forEach { operation ->
            val entitySelectionProblem = when (operation) {
                is CloneEntitiesRequest -> validateEntitySelection(
                    operation.sourceMin,
                    operation.sourceMax,
                    operation.entitySelection,
                )
                is MoveEntitiesRequest -> validateEntitySelection(
                    operation.sourceMin,
                    operation.sourceMax,
                    operation.entitySelection,
                )
                else -> null
            }
            if (entitySelectionProblem != null) {
                return rejected(
                    requestId = request.requestId,
                    code = entitySelectionProblem.first,
                    message = entitySelectionProblem.second,
                )
            }
            val invalidPos = when (operation) {
                is ExtrudeRequest -> {
                    val planned = AxionFabricServerExtrudePlanner(world).plan(operation)
                    plannedExtrudes[operation] = planned
                    planned.touchedPositions.firstOrNull { touched ->
                        !world.isInBuildLimit(BlockPos(touched.x, touched.y, touched.z))
                    }
                }
                else -> firstOutOfBounds(world, operation)
            }
            if (invalidPos != null) {
                return rejected(
                    requestId = request.requestId,
                    code = AxionResultCode.WORLD_MISMATCH,
                    message = "Edit extends outside the world build limits.",
                    blockedPosition = invalidPos,
                )
            }
        }

        val entityMoves = mutableListOf<FabricCommittedEntityMove>()
        val entityClones = mutableListOf<FabricCommittedEntityClone>()
        val entityDeletes = mutableListOf<FabricCommittedEntityClone>()
        val touchedOverride = AxionFabricCommittedDiffBuilder.collectTouched(request.operations).toMutableSet()
        plannedExtrudes.values.forEach { touchedOverride += it.touchedPositions }
        val transactionId = if (request.recordHistory) history.nextTransactionId() else null

        val result = AxionFabricCommittedDiffBuilder(world).build(
            requestId = request.requestId,
            label = actionLabel(request.operations),
            operations = request.operations,
            transactionId = transactionId,
            touchedOverride = touchedOverride,
        ) {
            request.operations.forEach { operation ->
                when (operation) {
                    is ClearRegionRequest -> applyClear(world, operation)
                    is CloneRegionRequest -> applyClone(world, operation)
                    is FilteredCloneRegionRequest -> applyFilteredClone(world, operation)
                    is CloneEntitiesRequest -> entityClones += AxionFabricEntityCloneService.clone(world, operation)
                    is DeleteEntitiesRequest -> entityDeletes += AxionFabricEntityDeleteService.delete(world, operation)
                    is MoveEntitiesRequest -> entityMoves += AxionFabricEntityMoveService.move(world, operation)
                    is PlaceBlocksRequest -> applyPlacements(world, operation)
                    is StackRegionRequest -> applyRepeatedClipboard(world, operation.sourceOrigin, operation.cells, operation.step, operation.repeatCount, airOnly = operation.keepExisting)
                    is SmearRegionRequest -> applySmearedClipboard(world, operation)
                    is ExtrudeRequest -> applyExtrude(world, plannedExtrudes[operation])
                    else -> Unit
                }
            }
        }
        if (transactionId != null && (result.changes.isNotEmpty() || entityMoves.isNotEmpty() || entityClones.isNotEmpty() || entityDeletes.isNotEmpty())) {
            history.recordNormal(
                playerId = player.uuid,
                transaction = FabricHistoryTransaction(
                    id = transactionId,
                    label = result.actionLabel ?: "Edit",
                    worldKey = world.registryKey.value.toString(),
                    changes = result.changes,
                    entityMoves = entityMoves,
                    entityClones = entityClones,
                    entityDeletes = entityDeletes,
                ),
            )
        }
        return result
    }

    private fun applyClear(world: ServerWorld, operation: ClearRegionRequest) {
        val min = minVector(operation.min, operation.max)
        val max = maxVector(operation.min, operation.max)
        forEachPos(min, max) { pos ->
            AxionFabricBlockEntitySnapshotService.apply(
                world = world,
                pos = pos,
                state = net.minecraft.block.Blocks.AIR.defaultState,
                blockEntityData = null,
            )
        }
    }

    private fun applyClone(world: ServerWorld, operation: CloneRegionRequest) {
        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
        val captured = buildList {
            forEachPos(sourceMin, sourceMax) { pos ->
                add(
                    CapturedBlock(
                        offset = IntVector3(pos.x - sourceMin.x, pos.y - sourceMin.y, pos.z - sourceMin.z),
                        state = world.getBlockState(pos),
                        blockEntityData = AxionFabricBlockEntitySnapshotService.capture(world, pos),
                    ),
                )
            }
        }

        captured.forEach { block ->
            val destination = BlockPos(
                operation.destinationOrigin.x + block.offset.x,
                operation.destinationOrigin.y + block.offset.y,
                operation.destinationOrigin.z + block.offset.z,
            )
            AxionFabricBlockEntitySnapshotService.apply(world, destination, block.state, block.blockEntityData)
        }
    }

    private fun applyFilteredClone(world: ServerWorld, operation: FilteredCloneRegionRequest) {
        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
        val captured = buildList {
            forEachPos(sourceMin, sourceMax) { pos ->
                add(
                    CapturedBlock(
                        offset = IntVector3(pos.x - sourceMin.x, pos.y - sourceMin.y, pos.z - sourceMin.z),
                        state = world.getBlockState(pos),
                        blockEntityData = AxionFabricBlockEntitySnapshotService.capture(world, pos),
                    ),
                )
            }
        }

        captured.forEach { block ->
            if (!operation.copyAir && block.state.isAir) {
                return@forEach
            }

            val destination = BlockPos(
                operation.destinationOrigin.x + block.offset.x,
                operation.destinationOrigin.y + block.offset.y,
                operation.destinationOrigin.z + block.offset.z,
            )

            if (operation.keepExisting && !world.getBlockState(destination).isAir) {
                return@forEach
            }

            AxionFabricBlockEntitySnapshotService.apply(world, destination, block.state, block.blockEntityData)
        }
    }

    private fun applyPlacements(world: ServerWorld, operation: PlaceBlocksRequest) {
        operation.placements.forEach { placement ->
            val parsed = parseBlockState(world, placement.blockState) ?: return@forEach
            AxionFabricBlockEntitySnapshotService.apply(
                world = world,
                pos = BlockPos(placement.pos.x, placement.pos.y, placement.pos.z),
                state = parsed.blockState(),
                blockEntityData = placement.blockEntityData,
            )
        }
    }

    private fun applyExtrude(
        world: ServerWorld,
        planned: AxionFabricServerExtrudePlanner.PlannedExtrude?,
    ) {
        planned?.writes?.forEach { write ->
            val parsed = parseBlockState(world, write.blockState) ?: return@forEach
            AxionFabricBlockEntitySnapshotService.apply(
                world = world,
                pos = BlockPos(write.pos.x, write.pos.y, write.pos.z),
                state = parsed.blockState(),
                blockEntityData = write.blockEntityData,
            )
        }
    }

    private fun applyRepeatedClipboard(
        world: ServerWorld,
        sourceOrigin: IntVector3,
        cells: List<ClipboardCellPayload>,
        step: IntVector3,
        repeatCount: Int,
        airOnly: Boolean,
    ) {
        for (repeatIndex in 1..repeatCount) {
            val offsetX = step.x * repeatIndex
            val offsetY = step.y * repeatIndex
            val offsetZ = step.z * repeatIndex
            cells.forEach { cell ->
                val destination = BlockPos(
                    sourceOrigin.x + cell.offset.x + offsetX,
                    sourceOrigin.y + cell.offset.y + offsetY,
                    sourceOrigin.z + cell.offset.z + offsetZ,
                )
                if (airOnly && !world.getBlockState(destination).isAir) {
                    return@forEach
                }
                val parsed = parseBlockState(world, cell.blockState) ?: return@forEach
                AxionFabricBlockEntitySnapshotService.apply(
                    world = world,
                    pos = destination,
                    state = parsed.blockState(),
                    blockEntityData = cell.blockEntityData,
                )
            }
        }
    }

    private fun applySmearedClipboard(world: ServerWorld, operation: SmearRegionRequest) {
        val offsets = smearOffsets(operation.step, operation.repeatCount)
        if (offsets.isEmpty()) {
            return
        }

        val sourcePositions = operation.cells.mapTo(linkedSetOf()) { cell ->
            BlockPos(
                operation.sourceOrigin.x + cell.offset.x,
                operation.sourceOrigin.y + cell.offset.y,
                operation.sourceOrigin.z + cell.offset.z,
            )
        }
        val candidates = linkedMapOf<BlockPos, SmearCandidate>()

        for (cell in operation.cells) {
            val parsed = parseBlockState(world, cell.blockState) ?: continue
            val state = parsed.blockState()
            if (state.isAir) {
                continue
            }

            for (offset in offsets) {
                val destination = BlockPos(
                    operation.sourceOrigin.x + cell.offset.x + offset.x,
                    operation.sourceOrigin.y + cell.offset.y + offset.y,
                    operation.sourceOrigin.z + cell.offset.z + offset.z,
                )
                if (destination !in sourcePositions && !world.getBlockState(destination).isAir) {
                    break
                }
                val distanceSq = offset.x * offset.x + offset.y * offset.y + offset.z * offset.z
                val existing = candidates[destination]
                if (existing == null || distanceSq < existing.distanceSq) {
                    candidates[destination] = SmearCandidate(
                        pos = destination,
                        state = state,
                        blockEntityData = cell.blockEntityData,
                        distanceSq = distanceSq,
                    )
                }
            }
        }

        candidates.values
            .sortedWith(compareBy<SmearCandidate> { it.pos.x }.thenBy { it.pos.y }.thenBy { it.pos.z })
            .forEach { candidate ->
                AxionFabricBlockEntitySnapshotService.apply(
                    world = world,
                    pos = candidate.pos,
                    state = candidate.state,
                    blockEntityData = candidate.blockEntityData,
                )
            }
    }

    private fun resolveServerWorld(player: ServerPlayerEntity): ServerWorld {
        val playerClass = player.javaClass
        val directWorld = runCatching {
            playerClass.methods.firstOrNull { it.name == "getServerWorld" }?.invoke(player) as? ServerWorld
        }.onFailure { e ->
            AxionFabricServerMod.LOGGER.debug("getServerWorld reflection failed for {}: {}", playerClass.name, e.message)
        }.getOrNull()
        if (directWorld != null) {
            return directWorld
        }

        val genericWorld = runCatching {
            playerClass.methods.firstOrNull { it.name == "getWorld" }?.invoke(player) as? ServerWorld
        }.onFailure { e ->
            AxionFabricServerMod.LOGGER.debug("getWorld reflection failed for {}: {}", playerClass.name, e.message)
        }.getOrNull()
        if (genericWorld != null) {
            return genericWorld
        }

        error("Unable to resolve ServerWorld for Axion Fabric operation handling")
    }

    private fun firstOutOfBounds(world: ServerWorld, operation: AxionRemoteOperation): IntVector3? {
        return when (operation) {
            is ClearRegionRequest -> firstOutOfBounds(world, minVector(operation.min, operation.max), maxVector(operation.min, operation.max))
            is CloneRegionRequest -> {
                firstOutOfBounds(world, minVector(operation.sourceMin, operation.sourceMax), maxVector(operation.sourceMin, operation.sourceMax))
                    ?: run {
                        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
                        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
                        firstOutOfBounds(
                            world,
                            operation.destinationOrigin,
                            IntVector3(
                                operation.destinationOrigin.x + (sourceMax.x - sourceMin.x),
                                operation.destinationOrigin.y + (sourceMax.y - sourceMin.y),
                                operation.destinationOrigin.z + (sourceMax.z - sourceMin.z),
                            ),
                        )
                    }
            }

            is FilteredCloneRegionRequest -> {
                firstOutOfBounds(world, minVector(operation.sourceMin, operation.sourceMax), maxVector(operation.sourceMin, operation.sourceMax))
                    ?: run {
                        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
                        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
                        firstOutOfBounds(
                            world,
                            operation.destinationOrigin,
                            IntVector3(
                                operation.destinationOrigin.x + (sourceMax.x - sourceMin.x),
                                operation.destinationOrigin.y + (sourceMax.y - sourceMin.y),
                                operation.destinationOrigin.z + (sourceMax.z - sourceMin.z),
                            ),
                        )
                    }
            }

            is PlaceBlocksRequest -> operation.placements.firstNotNullOfOrNull { placement ->
                val pos = BlockPos(placement.pos.x, placement.pos.y, placement.pos.z)
                if (world.isInBuildLimit(pos)) null else placement.pos
            }
            is StackRegionRequest -> firstOutOfBoundsRepeated(world, operation.sourceOrigin, operation.cells, operation.step, operation.repeatCount)
            is SmearRegionRequest -> firstOutOfBoundsSmeared(world, operation.sourceOrigin, operation.cells, operation.step, operation.repeatCount)
            is CloneEntitiesRequest -> firstOutOfBounds(world, minVector(operation.sourceMin, operation.sourceMax), maxVector(operation.sourceMin, operation.sourceMax))
            is DeleteEntitiesRequest -> firstOutOfBounds(world, minVector(operation.sourceMin, operation.sourceMax), maxVector(operation.sourceMin, operation.sourceMax))
            is MoveEntitiesRequest -> {
                firstOutOfBounds(world, minVector(operation.sourceMin, operation.sourceMax), maxVector(operation.sourceMin, operation.sourceMax))
                    ?: run {
                        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
                        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
                        firstOutOfBounds(
                            world,
                            operation.destinationOrigin,
                            IntVector3(
                                operation.destinationOrigin.x + (sourceMax.x - sourceMin.x),
                                operation.destinationOrigin.y + (sourceMax.y - sourceMin.y),
                                operation.destinationOrigin.z + (sourceMax.z - sourceMin.z),
                            ),
                        )
                    }
            }

            else -> null
        }
    }

    private fun validateEntitySelection(
        sourceMin: IntVector3,
        sourceMax: IntVector3,
        selection: EntitySelectionMask,
    ): Pair<AxionResultCode, String>? {
        val size = try {
            EntitySelectionGeometry.sourceSize(sourceMin, sourceMax)
        } catch (_: IllegalArgumentException) {
            return AxionResultCode.VALIDATION_FAILED to "Entity selection bounds are too large"
        }
        if (!selection.isValidFor(size)) {
            return AxionResultCode.VALIDATION_FAILED to "Entity selection offset is outside its source region"
        }
        if (selection.selectedBlockCount(size) > MAX_ENTITY_SELECTION_CELLS) {
            return AxionResultCode.CLIPBOARD_LIMIT_EXCEEDED to "Entity selection exceeds the transport limit"
        }
        return null
    }

    private fun firstOutOfBoundsRepeated(
        world: ServerWorld,
        sourceOrigin: IntVector3,
        cells: List<ClipboardCellPayload>,
        step: IntVector3,
        repeatCount: Int,
    ): IntVector3? {
        for (repeatIndex in 1..repeatCount) {
            val offsetX = step.x * repeatIndex
            val offsetY = step.y * repeatIndex
            val offsetZ = step.z * repeatIndex
            for (cell in cells) {
                val pos = BlockPos(
                    sourceOrigin.x + cell.offset.x + offsetX,
                    sourceOrigin.y + cell.offset.y + offsetY,
                    sourceOrigin.z + cell.offset.z + offsetZ,
                )
                if (!world.isInBuildLimit(pos)) {
                    return IntVector3(pos.x, pos.y, pos.z)
                }
            }
        }
        return null
    }

    private fun firstOutOfBoundsSmeared(
        world: ServerWorld,
        sourceOrigin: IntVector3,
        cells: List<ClipboardCellPayload>,
        offset: IntVector3,
        repeatCount: Int,
    ): IntVector3? {
        smearOffsets(offset, repeatCount).forEach { smearOffset ->
            cells.forEach { cell ->
                val pos = BlockPos(
                    sourceOrigin.x + cell.offset.x + smearOffset.x,
                    sourceOrigin.y + cell.offset.y + smearOffset.y,
                    sourceOrigin.z + cell.offset.z + smearOffset.z,
                )
                if (!world.isInBuildLimit(pos)) {
                    return IntVector3(pos.x, pos.y, pos.z)
                }
            }
        }
        return null
    }

    private fun firstOutOfBounds(world: ServerWorld, a: IntVector3, b: IntVector3): IntVector3? {
        val min = minVector(a, b)
        val max = maxVector(a, b)
        for (x in min.x..max.x) {
            for (y in min.y..max.y) {
                for (z in min.z..max.z) {
                    val pos = BlockPos(x, y, z)
                    if (!world.isInBuildLimit(pos)) {
                        return IntVector3(x, y, z)
                    }
                }
            }
        }
        return null
    }

    private fun forEachPos(min: IntVector3, max: IntVector3, block: (BlockPos) -> Unit) {
        for (x in min.x..max.x) {
            for (y in min.y..max.y) {
                for (z in min.z..max.z) {
                    block(BlockPos(x, y, z))
                }
            }
        }
    }

    private fun actionLabel(operations: List<AxionRemoteOperation>): String {
        val hasClone = operations.any { it is CloneRegionRequest }
        val hasClear = operations.any { it is ClearRegionRequest }
        return when {
            operations.any { it is MoveEntitiesRequest } -> "Move"
            hasClone && hasClear -> "Move"
            hasClone -> "Clone"
            operations.any { it is StackRegionRequest } -> "Stack"
            operations.any { it is SmearRegionRequest } -> "Smear"
            operations.any { it is ExtrudeRequest } -> "Extrude"
            operations.any { it is PlaceBlocksRequest } -> "Place"
            operations.all { it is ClearRegionRequest } -> "Erase"
            else -> "Edit"
        }
    }

    private fun rejected(
        requestId: Long,
        code: AxionResultCode,
        message: String,
        blockedPosition: IntVector3? = null,
    ): OperationBatchResult {
        return OperationBatchResult(
            requestId = requestId,
            accepted = false,
            message = message,
            changedBlockCount = 0,
            code = code,
            source = AxionResultSource.SERVER,
            blockedPosition = blockedPosition,
        )
    }

    private fun minVector(a: IntVector3, b: IntVector3): IntVector3 {
        return IntVector3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
    }

    private fun maxVector(a: IntVector3, b: IntVector3): IntVector3 {
        return IntVector3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
    }

    private fun smearOffsets(offset: IntVector3, steps: Int): List<IntVector3> {
        if (steps <= 0) {
            return emptyList()
        }
        return (1..steps).map { index ->
            IntVector3(
                java.lang.Math.round(index * offset.x.toFloat() / steps),
                java.lang.Math.round(index * offset.y.toFloat() / steps),
                java.lang.Math.round(index * offset.z.toFloat() / steps),
            )
        }.distinct()
    }

    private data class CapturedBlock(
        val offset: IntVector3,
        val state: net.minecraft.block.BlockState,
        val blockEntityData: String?,
    )

    private data class SmearCandidate(
        val pos: BlockPos,
        val state: net.minecraft.block.BlockState,
        val blockEntityData: String?,
        val distanceSq: Int,
    )

    companion object {
        private const val ENCODED_ENTITY_OFFSET_BYTES: Long = 3L * Int.SIZE_BYTES
        private val MAX_ENTITY_SELECTION_CELLS: Long =
            AxionTransportCodec.MAX_SERIALIZED_BYTES.toLong() / ENCODED_ENTITY_OFFSET_BYTES

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

    private fun parseBlockState(world: ServerWorld, value: String): BlockResult? {
        return try {
            BlockArgumentParser.block(
                world.createCommandRegistryWrapper(RegistryKeys.BLOCK),
                StringReader(value),
                true,
            )
        } catch (e: Exception) {
            AxionFabricServerMod.LOGGER.warn("Failed to parse block state '{}': {}", value, e.message)
            null
        }
    }
}

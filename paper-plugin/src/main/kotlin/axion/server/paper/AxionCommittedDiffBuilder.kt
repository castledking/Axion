package axion.server.paper

import axion.protocol.AxionRemoteOperation
import axion.protocol.ClearRegionRequest
import axion.protocol.CloneRegionRequest
import axion.protocol.CommittedBlockChangePayload
import axion.protocol.ExtrudeRequest
import axion.protocol.IntVector3
import axion.protocol.OperationBatchResult
import axion.protocol.PlaceBlocksRequest
import axion.protocol.SmearRegionRequest
import axion.protocol.StackRegionRequest
import net.minecraft.core.BlockPos
import org.bukkit.World

class AxionCommittedDiffBuilder(
    private val world: World,
) {
    fun build(
        requestId: Long,
        transactionId: Long?,
        label: String,
        operations: List<AxionRemoteOperation>,
        touchedOverride: Set<IntVector3>? = null,
        timing: AxionTimingContext? = null,
        apply: () -> Unit,
    ): OperationBatchResult {
        val touched = touchedOverride ?: collectTouched(operations)

        val before = measureDiff(timing) { touched.associateWith(::snapshot) }
        measureApply(timing) { apply() }
        val changes = measureDiff(timing) {
            touched.mapNotNull { pos ->
                val oldState = before[pos] ?: return@mapNotNull null
                val newState = snapshot(pos)
                if (oldState == newState) {
                    null
                } else {
                    CommittedBlockChangePayload(
                        pos = pos,
                        oldState = oldState.blockState,
                        newState = newState.blockState,
                        oldBlockEntityData = oldState.blockEntityData,
                        newBlockEntityData = newState.blockEntityData,
                    )
                }
            }
        }

        return OperationBatchResult(
            requestId = requestId,
            accepted = true,
            message = "Applied",
            changedBlockCount = changes.size,
            transactionId = transactionId,
            actionLabel = label,
            changes = changes,
        )
    }

    companion object {
        private fun <T> measureApply(timing: AxionTimingContext?, block: () -> T): T {
            return timing?.measureApply(block) ?: block()
        }

        private fun <T> measureDiff(timing: AxionTimingContext?, block: () -> T): T {
            return timing?.measureDiff(block) ?: block()
        }

        fun collectTouched(operations: List<AxionRemoteOperation>): Set<IntVector3> {
            val touched = linkedSetOf<IntVector3>()
            operations.forEach { operation ->
                when (operation) {
                    is ClearRegionRequest -> collectRegion(touched, operation.min, operation.max)
                    is CloneRegionRequest -> {
                        val sourceMin = minVector(operation.sourceMin, operation.sourceMax)
                        val sourceMax = maxVector(operation.sourceMin, operation.sourceMax)
                        val size = IntVector3(
                            sourceMax.x - sourceMin.x,
                            sourceMax.y - sourceMin.y,
                            sourceMax.z - sourceMin.z,
                        )
                        collectRegion(
                            touched,
                            operation.destinationOrigin,
                            IntVector3(
                                operation.destinationOrigin.x + size.x,
                                operation.destinationOrigin.y + size.y,
                                operation.destinationOrigin.z + size.z,
                            ),
                        )
                    }

                    is StackRegionRequest -> collectRepeatedClipboard(touched, operation.sourceOrigin, operation.cells, operation.step, operation.repeatCount)
                    is SmearRegionRequest -> collectRepeatedClipboard(touched, operation.sourceOrigin, operation.cells, operation.step, operation.repeatCount)
                    is ExtrudeRequest -> Unit
                    is PlaceBlocksRequest -> operation.placements.forEach { touched += it.pos }
                }
            }
            return touched
        }

        private fun collectRegion(target: MutableSet<IntVector3>, a: IntVector3, b: IntVector3) {
            val min = minVector(a, b)
            val max = maxVector(a, b)
            for (x in min.x..max.x) {
                for (y in min.y..max.y) {
                    for (z in min.z..max.z) {
                        target += IntVector3(x, y, z)
                    }
                }
            }
        }

        private fun collectRepeatedClipboard(
            target: MutableSet<IntVector3>,
            sourceOrigin: IntVector3,
            cells: List<axion.protocol.ClipboardCellPayload>,
            step: IntVector3,
            repeatCount: Int,
        ) {
            for (repeatIndex in 1..repeatCount) {
                val offsetX = step.x * repeatIndex
                val offsetY = step.y * repeatIndex
                val offsetZ = step.z * repeatIndex
                cells.forEach { cell ->
                    target += IntVector3(
                        sourceOrigin.x + cell.offset.x + offsetX,
                        sourceOrigin.y + cell.offset.y + offsetY,
                        sourceOrigin.z + cell.offset.z + offsetZ,
                    )
                }
            }
        }

        private fun minVector(a: IntVector3, b: IntVector3): IntVector3 {
            return IntVector3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
        }

        private fun maxVector(a: IntVector3, b: IntVector3): IntVector3 {
            return IntVector3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
        }
    }

    private fun snapshot(pos: IntVector3): BlockSnapshot {
        val blockPos = BlockPos(pos.x, pos.y, pos.z)
        return BlockSnapshot(
            blockState = world.getBlockAt(pos.x, pos.y, pos.z).blockData.getAsString(false),
            blockEntityData = PaperBlockEntitySnapshotService.capture(world, blockPos),
        )
    }

    private data class BlockSnapshot(
        val blockState: String,
        val blockEntityData: String?,
    )
}

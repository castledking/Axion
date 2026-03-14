package axion.server.paper

import axion.protocol.AxionRemoteOperation
import axion.protocol.ClearRegionRequest
import axion.protocol.CloneRegionRequest
import axion.protocol.ExtrudeRequest
import axion.protocol.IntVector3
import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.SmearRegionRequest
import axion.protocol.StackRegionRequest
import org.bukkit.World

class AxionOperationValidator(
    private val world: World,
    private val policy: AxionWorldPolicy,
) {
    fun validate(operation: AxionRemoteOperation): AxionRejection? {
        return when (operation) {
            is ClearRegionRequest -> validateBounds(operation.min, operation.max)
            is CloneRegionRequest -> {
                validateBounds(operation.sourceMin, operation.sourceMax)
                    ?: validateBounds(
                        operation.destinationOrigin,
                        IntVector3(
                            operation.destinationOrigin.x + abs(operation.sourceMax.x - operation.sourceMin.x),
                            operation.destinationOrigin.y + abs(operation.sourceMax.y - operation.sourceMin.y),
                            operation.destinationOrigin.z + abs(operation.sourceMax.z - operation.sourceMin.z),
                        ),
                    )
            }

            is StackRegionRequest -> validateRepeatedClipboard(operation.sourceOrigin, operation.cells, operation.step, operation.repeatCount)
            is SmearRegionRequest -> validateRepeatedClipboard(operation.sourceOrigin, operation.cells, operation.step, operation.repeatCount)
            is ExtrudeRequest -> validateExtrude(operation)
        }
    }

    fun blockCount(operation: AxionRemoteOperation): Int {
        return when (operation) {
            is ClearRegionRequest -> blockCount(operation.min, operation.max)
            is CloneRegionRequest -> blockCount(operation.sourceMin, operation.sourceMax)
            is StackRegionRequest -> operation.cells.size * maxOf(operation.repeatCount, 0)
            is SmearRegionRequest -> operation.cells.size * maxOf(operation.repeatCount, 0)
            is ExtrudeRequest -> policy.maxExtrudeWrites
        }
    }

    private fun validateBounds(a: IntVector3, b: IntVector3): AxionRejection? {
        val min = IntVector3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
        val max = IntVector3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
        if (min.y < world.minHeight || max.y >= world.maxHeight) {
            return AxionRejection(
                code = AxionResultCode.VALIDATION_FAILED,
                source = AxionResultSource.REQUEST,
                message = "Edit is outside build height",
            )
        }
        return null
    }

    private fun blockCount(a: IntVector3, b: IntVector3): Int {
        val min = IntVector3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
        val max = IntVector3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
        return (max.x - min.x + 1) * (max.y - min.y + 1) * (max.z - min.z + 1)
    }

    private fun validateRepeatedClipboard(
        sourceOrigin: IntVector3,
        cells: List<axion.protocol.ClipboardCellPayload>,
        step: IntVector3,
        repeatCount: Int,
    ): AxionRejection? {
        if (repeatCount <= 0) {
            return AxionRejection(
                code = AxionResultCode.REPEAT_LIMIT_EXCEEDED,
                source = AxionResultSource.REQUEST,
                message = "Repeat count must be positive",
            )
        }

        if (repeatCount > policy.maxRepeatCount) {
            return AxionRejection(
                code = AxionResultCode.REPEAT_LIMIT_EXCEEDED,
                source = AxionResultSource.POLICY,
                message = "Repeat count exceeds the configured limit for world ${world.name}",
            )
        }

        if (cells.size > policy.maxClipboardCells) {
            return AxionRejection(
                code = AxionResultCode.CLIPBOARD_LIMIT_EXCEEDED,
                source = AxionResultSource.POLICY,
                message = "Clipboard is too large",
            )
        }

        if (cells.size.toLong() * repeatCount.toLong() > policy.maxTotalWrites.toLong()) {
            return AxionRejection(
                code = AxionResultCode.WRITE_LIMIT_EXCEEDED,
                source = AxionResultSource.POLICY,
                message = "Edit expands to too many writes",
            )
        }

        for (repeatIndex in 1..repeatCount) {
            val offsetX = step.x * repeatIndex
            val offsetY = step.y * repeatIndex
            val offsetZ = step.z * repeatIndex
            cells.forEach { cell ->
                val y = sourceOrigin.y + cell.offset.y + offsetY
                if (y < world.minHeight || y >= world.maxHeight) {
                    return AxionRejection(
                        code = AxionResultCode.VALIDATION_FAILED,
                        source = AxionResultSource.REQUEST,
                        message = "Edit is outside build height",
                    )
                }
            }
        }
        return null
    }

    private fun validateExtrude(operation: ExtrudeRequest): AxionRejection? {
        val origin = operation.origin
        if (origin.y < world.minHeight || origin.y >= world.maxHeight) {
            return AxionRejection(
                code = AxionResultCode.VALIDATION_FAILED,
                source = AxionResultSource.REQUEST,
                message = "Edit is outside build height",
            )
        }
        val direction = operation.direction
        if (kotlin.math.abs(direction.x) + kotlin.math.abs(direction.y) + kotlin.math.abs(direction.z) != 1) {
            return AxionRejection(
                code = AxionResultCode.VALIDATION_FAILED,
                source = AxionResultSource.REQUEST,
                message = "Extrude direction must be cardinal",
            )
        }
        return null
    }

    companion object {
    }
}

private fun abs(value: Int): Int = kotlin.math.abs(value)

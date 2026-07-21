package axion.server.paper

import axion.protocol.AxionInteractionOrigin
import axion.protocol.AxionRemoteOperation
import axion.protocol.ClearRegionRequest
import axion.protocol.IntVector3
import axion.protocol.PlaceBlocksRequest

internal enum class PaperInteractionEventKind {
    BREAK,
    PLACE,
}

internal enum class PaperInteractionRequestProblem {
    BULK_CLEAR,
    TOO_MANY_WRITES,
    UNSUPPORTED_OPERATION,
}

internal data class PaperInteractionBatchRoute(
    val eventEligible: Boolean,
    val inferred: Boolean,
    val writeCount: Int,
    val problem: PaperInteractionRequestProblem? = null,
)

/**
 * Routes explicitly tagged and markerless non-history interactions to Bukkit events.
 * Bulk Axion tools retain their existing batched policy adapters, while each eligible
 * write is independently checked against vanilla reach before an event is emitted.
 */
internal object PaperInteractionEventPolicy {
    const val MAX_INTERACTION_WRITES: Int = 256
    private const val VANILLA_CREATIVE_REACH: Double = 6.0

    fun eventKinds(
        origin: AxionInteractionOrigin,
        stateChanged: Boolean,
        oldIsAir: Boolean,
        newIsAir: Boolean,
    ): List<PaperInteractionEventKind> {
        if (origin != AxionInteractionOrigin.INFINITE_REACH || !stateChanged || oldIsAir && newIsAir) {
            return emptyList()
        }

        return buildList {
            if (!oldIsAir) {
                add(PaperInteractionEventKind.BREAK)
            }
            if (!newIsAir) {
                add(PaperInteractionEventKind.PLACE)
            }
        }
    }

    fun validateOperations(
        origin: AxionInteractionOrigin,
        operations: List<AxionRemoteOperation>,
    ): PaperInteractionRequestProblem? = routeBatch(
        origin = origin,
        recordHistory = true,
        operations = operations,
    ).problem

    fun routeBatch(
        origin: AxionInteractionOrigin,
        recordHistory: Boolean,
        operations: List<AxionRemoteOperation>,
    ): PaperInteractionBatchRoute {
        val explicitlyTagged = origin == AxionInteractionOrigin.INFINITE_REACH
        if (!explicitlyTagged && recordHistory) {
            return PaperInteractionBatchRoute(
                eventEligible = false,
                inferred = false,
                writeCount = 0,
            )
        }

        var writeCount = 0
        operations.forEach { operation ->
            when (operation) {
                is ClearRegionRequest -> if (operation.min != operation.max) {
                    return if (explicitlyTagged) {
                        PaperInteractionBatchRoute(
                            eventEligible = true,
                            inferred = false,
                            writeCount = writeCount,
                            problem = PaperInteractionRequestProblem.BULK_CLEAR,
                        )
                    } else {
                        PaperInteractionBatchRoute(
                            eventEligible = false,
                            inferred = false,
                            writeCount = 0,
                        )
                    }
                } else {
                    writeCount++
                }

                is PlaceBlocksRequest -> writeCount += operation.placements.size
                else -> return if (explicitlyTagged) {
                    PaperInteractionBatchRoute(
                        eventEligible = true,
                        inferred = false,
                        writeCount = writeCount,
                        problem = PaperInteractionRequestProblem.UNSUPPORTED_OPERATION,
                    )
                } else {
                    PaperInteractionBatchRoute(
                        eventEligible = false,
                        inferred = false,
                        writeCount = 0,
                    )
                }
            }
        }

        val inferred = !explicitlyTagged && !recordHistory && writeCount > 0
        if (!explicitlyTagged && !inferred) {
            return PaperInteractionBatchRoute(
                eventEligible = false,
                inferred = false,
                writeCount = 0,
            )
        }
        return PaperInteractionBatchRoute(
            eventEligible = true,
            inferred = inferred,
            writeCount = writeCount,
            problem = if (writeCount > MAX_INTERACTION_WRITES) {
                PaperInteractionRequestProblem.TOO_MANY_WRITES
            } else {
                null
            },
        )
    }

    fun originForWrite(
        route: PaperInteractionBatchRoute,
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double,
        pos: IntVector3,
    ): AxionInteractionOrigin {
        if (!route.eventEligible || route.problem != null) {
            return AxionInteractionOrigin.NONE
        }

        val distanceSquared = axisDistance(eyeX, pos.x.toDouble(), pos.x + 1.0).let { it * it } +
            axisDistance(eyeY, pos.y.toDouble(), pos.y + 1.0).let { it * it } +
            axisDistance(eyeZ, pos.z.toDouble(), pos.z + 1.0).let { it * it }
        return if (distanceSquared > VANILLA_CREATIVE_REACH * VANILLA_CREATIVE_REACH) {
            AxionInteractionOrigin.INFINITE_REACH
        } else {
            AxionInteractionOrigin.NONE
        }
    }

    private fun axisDistance(value: Double, min: Double, max: Double): Double {
        return when {
            value < min -> min - value
            value > max -> value - max
            else -> 0.0
        }
    }
}

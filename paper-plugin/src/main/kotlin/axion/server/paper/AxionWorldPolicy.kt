package axion.server.paper

import axion.protocol.IntVector3

enum class AxionToolKind(
    val displayName: String,
) {
    ERASE("Erase"),
    CLONE("Clone"),
    MOVE("Move"),
    STACK("Stack"),
    SMEAR("Smear"),
    EXTRUDE("Extrude"),
}

data class HistoryBudget(
    val maxEntries: Int,
    val maxBytes: Int,
)

data class AxionEditRegion(
    val min: IntVector3,
    val max: IntVector3,
) {
    fun contains(pos: IntVector3): Boolean {
        return pos.x in min.x..max.x &&
            pos.y in min.y..max.y &&
            pos.z in min.z..max.z
    }
}

data class AxionWorldPolicy(
    val enabled: Boolean,
    val tools: Map<AxionToolKind, Boolean>,
    val maxBlocksPerBatch: Int,
    val maxClipboardCells: Int,
    val maxRepeatCount: Int,
    val maxTotalWrites: Int,
    val maxExtrudeFootprintSize: Int,
    val maxExtrudeWrites: Int,
    val historyBudget: HistoryBudget,
    val largeEditMultiplier: Int,
    val editRegion: AxionEditRegion?,
) {
    fun isToolEnabled(tool: AxionToolKind): Boolean = tools[tool] != false

    fun scaledForLargeEdits(): AxionWorldPolicy {
        val multiplier = largeEditMultiplier.coerceAtLeast(1)
        if (multiplier == 1) {
            return this
        }

        return copy(
            maxBlocksPerBatch = maxBlocksPerBatch * multiplier,
            maxClipboardCells = maxClipboardCells * multiplier,
            maxRepeatCount = maxRepeatCount * multiplier,
            maxTotalWrites = maxTotalWrites * multiplier,
            maxExtrudeFootprintSize = maxExtrudeFootprintSize * multiplier,
            maxExtrudeWrites = maxExtrudeWrites * multiplier,
        )
    }
}

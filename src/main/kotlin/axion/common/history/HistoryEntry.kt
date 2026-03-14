package axion.common.history

data class HistoryEntry(
    val id: Long,
    val timestampMillis: Long,
    val label: String,
    val changes: List<BlockChange>,
)

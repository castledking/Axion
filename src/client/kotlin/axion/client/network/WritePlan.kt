package axion.client.network

data class WritePlan(
    val label: String,
    val writes: List<BlockWrite>,
)

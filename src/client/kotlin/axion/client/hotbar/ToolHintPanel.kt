package axion.client.hotbar

data class ToolHintEntry(
    val input: String,
    val action: String,
)

data class ToolHintPanel(
    val title: String,
    val subtitle: String? = null,
    val entries: List<ToolHintEntry>,
    val statusLines: List<String> = emptyList(),
    val footer: String? = null,
)

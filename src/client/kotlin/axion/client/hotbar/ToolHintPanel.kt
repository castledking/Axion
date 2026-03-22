package axion.client.hotbar

import net.minecraft.text.Text

data class ToolHintEntry(
    val input: String,
    val action: String,
)

data class ToolHintPanel(
    val title: String,
    val subtitle: String? = null,
    val entries: List<ToolHintEntry>,
    val statusLines: List<Text> = emptyList(),
    val footer: String? = null,
)

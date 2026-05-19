package axion.client.hotbar

data class ToolHintEntry(
    val input: String,
    val action: String,
)

enum class MouseHintIcon {
    LEFT,
    RIGHT,
    SCROLL,
    NEUTRAL,
}

sealed interface CrosshairHint {
    val action: String

    data class Mouse(val icon: MouseHintIcon, override val action: String) : CrosshairHint
    data class Key(val key: String, override val action: String) : CrosshairHint
}

data class CompactToolHints(
    val crosshairHints: List<CrosshairHint> = emptyList(),
    val keyHints: List<ToolHintEntry> = emptyList(),
    val hotbarStatus: String? = null,
)

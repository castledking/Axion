package axion.client.ui

import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.ChatFormatting

object FormattedNameText {
    fun parse(raw: String): Component {
        if (raw.isEmpty()) {
            return Component.empty()
        }

        if ('&' !in raw && '#' !in raw) {
            return Component.literal(raw)
        }

        val root: MutableComponent = Component.literal("")
        val segment = StringBuilder()
        var style = Style.EMPTY
        var index = 0

        fun flush() {
            if (segment.isNotEmpty()) {
                root.append(Component.literal(segment.toString()).setStyle(style))
                segment.clear()
            }
        }

        while (index < raw.length) {
            val char = raw[index]
            if (char == '&' && index + 1 < raw.length) {
                formattingFor(raw[index + 1])?.let { formatting ->
                    flush()
                    style = if (formatting == ChatFormatting.RESET) {
                        Style.EMPTY
                    } else {
                        Style.EMPTY.withColor(formatting)
                    }
                    index += 2
                    continue
                }
            }
            if (char == '#' && index + 6 < raw.length) {
                val hex = raw.substring(index + 1, index + 7)
                hex.toIntOrNull(16)?.let { rgb ->
                    flush()
                    style = style.withColor(TextColor.fromRgb(rgb))
                    index += 7
                    continue
                }
            }
            segment.append(char)
            index += 1
        }

        flush()
        return root
    }

    private fun formattingFor(code: Char): ChatFormatting? {
        return when (code.lowercaseChar()) {
            '0' -> ChatFormatting.BLACK
            '1' -> ChatFormatting.DARK_BLUE
            '2' -> ChatFormatting.DARK_GREEN
            '3' -> ChatFormatting.DARK_AQUA
            '4' -> ChatFormatting.DARK_RED
            '5' -> ChatFormatting.DARK_PURPLE
            '6' -> ChatFormatting.GOLD
            '7' -> ChatFormatting.GRAY
            '8' -> ChatFormatting.DARK_GRAY
            '9' -> ChatFormatting.BLUE
            'a' -> ChatFormatting.GREEN
            'b' -> ChatFormatting.AQUA
            'c' -> ChatFormatting.RED
            'd' -> ChatFormatting.LIGHT_PURPLE
            'e' -> ChatFormatting.YELLOW
            'f' -> ChatFormatting.WHITE
            'r' -> ChatFormatting.RESET
            else -> null
        }
    }
}

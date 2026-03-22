package axion.client.ui

import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import net.minecraft.util.Formatting

object FormattedNameText {
    fun parse(raw: String): Text {
        if (raw.isEmpty()) {
            return Text.empty()
        }

        if ('&' !in raw && '#' !in raw) {
            return Text.literal(raw)
        }

        val root: MutableText = Text.literal("")
        val segment = StringBuilder()
        var style = Style.EMPTY
        var index = 0

        fun flush() {
            if (segment.isNotEmpty()) {
                root.append(Text.literal(segment.toString()).setStyle(style))
                segment.clear()
            }
        }

        while (index < raw.length) {
            val char = raw[index]
            if (char == '&' && index + 1 < raw.length) {
                formattingFor(raw[index + 1])?.let { formatting ->
                    flush()
                    style = if (formatting == Formatting.RESET) {
                        Style.EMPTY
                    } else {
                        style.withFormatting(formatting)
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

    private fun formattingFor(code: Char): Formatting? {
        return when (code.lowercaseChar()) {
            '0' -> Formatting.BLACK
            '1' -> Formatting.DARK_BLUE
            '2' -> Formatting.DARK_GREEN
            '3' -> Formatting.DARK_AQUA
            '4' -> Formatting.DARK_RED
            '5' -> Formatting.DARK_PURPLE
            '6' -> Formatting.GOLD
            '7' -> Formatting.GRAY
            '8' -> Formatting.DARK_GRAY
            '9' -> Formatting.BLUE
            'a' -> Formatting.GREEN
            'b' -> Formatting.AQUA
            'c' -> Formatting.RED
            'd' -> Formatting.LIGHT_PURPLE
            'e' -> Formatting.YELLOW
            'f' -> Formatting.WHITE
            'r' -> Formatting.RESET
            else -> null
        }
    }
}

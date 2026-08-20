package axion.client.config

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

class AxionConfigScreen(
    private val parent: Screen?,
) : Screen(Text.translatable("axion.config.title")) {
    private lateinit var infiniteReachRangeField: TextFieldWidget

    override fun init() {
        val centerX = width / 2
        val centerY = height / 2
        val macOnly = AxionClientConfig.isMacOs()
        val linuxOnly = AxionClientConfig.isLinux()

        // Main Mod toggle (macOS-only): Cmd <-> Ctrl
        addDrawableChild(
            ButtonWidget.builder(
                commandToggleLabel(),
            ) {
                if (macOnly) {
                    AxionClientConfig.setUseCommandModifierOnMac(!AxionClientConfig.useCommandModifierOnMac())
                    clearAndInit()
                }
            }.dimensions(centerX - 110, centerY - 55, 220, 20).build().apply {
                active = macOnly
            },
        )

        // Tool Mod toggle (Linux-only): Alt <-> Super
        addDrawableChild(
            ButtonWidget.builder(
                toolModifierToggleLabel(),
            ) {
                if (linuxOnly) {
                    AxionClientConfig.setUseSuperModifierOnLinux(!AxionClientConfig.useSuperModifierOnLinux())
                    clearAndInit()
                }
            }.dimensions(centerX - 110, centerY - 30, 220, 20).build().apply {
                active = linuxOnly
            },
        )

        infiniteReachRangeField = TextFieldWidget(
            textRenderer,
            centerX + 20,
            centerY - 5,
            90,
            20,
            Text.translatable("axion.config.infinite_reach_range"),
        ).apply {
            text = InfiniteReachRange.display(AxionClientConfig.configuredInfiniteReachRange())
            setMaxLength(12)
            setChangedListener { input ->
                when {
                    InfiniteReachRange.isUnlimitedInput(input) -> AxionClientConfig.setInfiniteReachRange(null)
                    input.trim().toDoubleOrNull()?.isFinite() == true -> {
                        AxionClientConfig.setInfiniteReachRange(InfiniteReachRange.parse(input))
                    }
                }
            }
        }
        addSelectableChild(infiniteReachRangeField)

        addDrawableChild(
            ButtonWidget.builder(
                Text.translatable("axion.config.magic_select.templates.button"),
            ) {
                client?.setScreen(MagicSelectMaskConfigScreen(this))
            }.dimensions(centerX - 110, centerY + 25, 220, 20).build(),
        )

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.done")) {
                close()
            }.dimensions(centerX - 100, centerY + 60, 200, 20).build(),
        )
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        // Avoid the shared blur path here; some modpacks/screens already consume it earlier in the frame.
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFF)
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.infinite_reach_range"),
            (width / 2) - 110,
            (height / 2) + 1,
            0xFFFFFF,
        )
        infiniteReachRangeField.render(context, mouseX, mouseY, deltaTicks)
    }

    private fun commandToggleLabel(): Text {
        return if (AxionClientConfig.isMacOs()) {
            val modifierKey = if (AxionClientConfig.useCommandModifierOnMac()) {
                "axion.config.main_modifier.cmd"
            } else {
                "axion.config.main_modifier.ctrl"
            }
            Text.translatable(
                "axion.config.main_modifier.button",
                Text.translatable(modifierKey),
            )
        } else {
            Text.translatable(
                "axion.config.main_modifier.button",
                Text.translatable("axion.config.main_modifier.ctrl"),
            )
        }
    }

    private fun toolModifierToggleLabel(): Text {
        val activeModifierKey = if (AxionClientConfig.useSuperModifierOnLinux()) {
            "axion.config.tool_modifier.super"
        } else {
            "axion.config.tool_modifier.alt"
        }
        return Text.translatable(
            "axion.config.tool_modifier.button",
            Text.translatable(activeModifierKey),
        )
    }
}

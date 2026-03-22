package axion.client.config

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

class AxionConfigScreen(
    private val parent: Screen?,
) : Screen(Text.translatable("axion.config.title")) {
    override fun init() {
        val centerX = width / 2
        val centerY = height / 2
        val macOnly = AxionClientConfig.isMacOs()

        addDrawableChild(
            ButtonWidget.builder(
                commandToggleLabel(),
            ) {
                if (macOnly) {
                    AxionClientConfig.setUseCommandModifierOnMac(!AxionClientConfig.useCommandModifierOnMac())
                    clearAndInit()
                }
            }.dimensions(centerX - 110, centerY - 10, 220, 20).build().apply {
                active = macOnly
            },
        )

        addDrawableChild(
            ButtonWidget.builder(
                Text.translatable("axion.config.magic_select.templates.button"),
            ) {
                client?.setScreen(MagicSelectMaskConfigScreen(this))
            }.dimensions(centerX - 110, centerY + 20, 220, 20).build(),
        )

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.done")) {
                close()
            }.dimensions(centerX - 100, centerY + 58, 200, 20).build(),
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
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.main_modifier.description"),
            width / 2,
            (height / 2) - 34,
            0xBFBFBF,
        )

        if (!AxionClientConfig.isMacOs()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("axion.config.main_modifier.mac_only"),
                width / 2,
                (height / 2) + 16,
                0x8A8A8A,
            )
        }

        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.templates.summary"),
            width / 2,
            (height / 2) + 2,
            0xBFBFBF,
        )
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
}

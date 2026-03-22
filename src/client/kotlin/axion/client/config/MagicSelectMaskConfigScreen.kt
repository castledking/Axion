package axion.client.config

import axion.client.ui.FormattedNameText
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

class MagicSelectMaskConfigScreen(
    private val parent: Screen?,
) : Screen(Text.translatable("axion.config.magic_select.title")) {
    private data class TemplateRow(
        val template: MagicSelectTemplateConfig,
        val contentX: Int,
        val y: Int,
    )

    private val rows = mutableListOf<TemplateRow>()

    override fun init() {
        rows.clear()
        val centerX = width / 2
        var y = 58

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("axion.config.magic_select.add.button")) {
                val templateId = AxionClientConfig.addMagicSelectTemplate()
                client?.setScreen(MagicSelectTemplateEditScreen(this, templateId))
            }.dimensions(centerX - 130, y, 260, 20).build(),
        )
        y += 30

        AxionClientConfig.magicSelectTemplates().forEach { template ->
            val contentX = centerX - 130
            rows += TemplateRow(template = template, contentX = contentX, y = y)
            addDrawableChild(
                ButtonWidget.builder(stateLabel(template)) {
                    AxionClientConfig.setMagicSelectTemplateEnabled(template.id, !template.enabled)
                    clearAndInit()
                }.dimensions(centerX + 30, y, 44, 20).build(),
            )
            addDrawableChild(
                ButtonWidget.builder(Text.translatable("axion.config.magic_select.edit.button")) {
                    client?.setScreen(MagicSelectTemplateEditScreen(this, template.id))
                }.dimensions(centerX + 80, y, 50, 20).build(),
            )
            y += 24
        }

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.back")) {
                close()
            }.dimensions(centerX - 100, height - 34, 200, 20).build(),
        )
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.description"),
            width / 2,
            34,
            0xBFBFBF,
        )
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.description2"),
            width / 2,
            44,
            0x8A8A8A,
        )

        rows.forEach { row ->
            AxionClientConfig.templateIcons(row.template).forEachIndexed { index, item ->
                context.drawItem(item.defaultStack, row.contentX + (index * 18), row.y + 2)
            }
            context.drawTextWithShadow(
                textRenderer,
                FormattedNameText.parse(row.template.name),
                row.contentX + 46,
                row.y + 6,
                0xFFFFFF,
            )
        }
    }

    private fun stateLabel(template: MagicSelectTemplateConfig): Text {
        return Text.translatable(if (template.enabled) "axion.config.toggle.on" else "axion.config.toggle.off")
    }
}

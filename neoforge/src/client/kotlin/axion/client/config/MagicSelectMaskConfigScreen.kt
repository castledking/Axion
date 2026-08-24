package axion.client.config

import axion.client.ui.FormattedNameText
import axion.client.ui.drawStrokedRectangleCompat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

class MagicSelectMaskConfigScreen(
    private val parent: Screen?,
) : Screen(Component.translatable("axion.config.magic_select.title")) {
    private data class TemplateRow(
        val template: MagicSelectTemplateConfig,
        val contentX: Int,
        val y: Int,
        val toggleX: Int,
        val toggleWidth: Int,
    )

    private val rows = mutableListOf<TemplateRow>()
    private val selectedBorderColor = 0xFF58D06F.toInt()
    private val idleBorderColor = 0xFFE05A5A.toInt()

    override fun init() {
        rows.clear()
        val centerX = width / 2
        val contentWidth = 360
        val leftX = centerX - (contentWidth / 2)
        var y = 58

        addRenderableWidget(
            Button.builder(Component.translatable("axion.config.magic_select.add.button")) {
                val templateId = AxionClientConfig.addMagicSelectTemplate()
                minecraft?.setScreen(MagicSelectTemplateEditScreen(this, templateId))
            }.bounds(leftX, y, contentWidth, 20).build(),
        )
        y += 30

        addRenderableWidget(
            Button.builder(Component.translatable("axion.config.magic_select.disable_all.button")) {
                AxionClientConfig.disableAllMagicSelectTemplates()
                rebuildWidgets()
            }.bounds(leftX, y, contentWidth, 20).build(),
        )
        y += 30

        addRenderableWidget(
            Button.builder(sameBlockSelectLabel()) {
                AxionClientConfig.toggleSameBlockMagicSelect()
                rebuildWidgets()
            }.bounds(leftX, y, contentWidth, 20).build(),
        )
        y += 30

        AxionClientConfig.magicSelectTemplates().forEach { template ->
            val contentX = leftX
            val toggleX = leftX + 46
            val toggleWidth = contentWidth - 100
            rows += TemplateRow(template = template, contentX = contentX, y = y, toggleX = toggleX, toggleWidth = toggleWidth)
            addRenderableWidget(
                Button.builder(toggleLabel(template.name, template.enabled)) {
                    AxionClientConfig.setMagicSelectTemplateEnabled(template.id, !template.enabled)
                    rebuildWidgets()
                }.bounds(toggleX, y, toggleWidth, 20).build(),
            )
            addRenderableWidget(
                Button.builder(Component.translatable("axion.config.magic_select.editWorld.button")) {
                    minecraft?.setScreen(MagicSelectTemplateEditScreen(this, template.id))
                }.bounds(leftX + contentWidth - 50, y, 50, 20).build(),
            )
            y += 24
        }

        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) {
                onClose()
            }.bounds(centerX - 100, height - 34, 200, 20).build(),
        )
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        context.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF)
        context.drawCenteredString(
            font,
            Component.translatable("axion.config.magic_select.description"),
            width / 2,
            34,
            0xBFBFBF,
        )
        context.drawCenteredString(
            font,
            Component.translatable("axion.config.magic_select.description2"),
            width / 2,
            44,
            0x8A8A8A,
        )

        rows.forEach { row ->
            AxionClientConfig.templateIcons(row.template).forEachIndexed { index, item ->
                context.renderItem(item.defaultInstance, row.contentX + (index * 18), row.y + 2)
            }
            context.drawStrokedRectangleCompat(
                row.toggleX,
                row.y,
                row.toggleWidth,
                20,
                if (row.template.enabled) selectedBorderColor else idleBorderColor,
            )
        }
    }

    private fun toggleLabel(name: String, enabled: Boolean): Component {
        return Component.empty()
            .append(FormattedNameText.parse(name))
            .append(Component.literal(": "))
            .append(Component.translatable(if (enabled) "axion.config.toggle.on" else "axion.config.toggle.off"))
    }

    private fun sameBlockSelectLabel(): Component {
        return Component.translatable(
            "axion.config.magic_select.same_block_select.button",
            Component.translatable(
                if (AxionClientConfig.sameBlockMagicSelectEnabled()) {
                    "axion.config.toggle.on"
                } else {
                    "axion.config.toggle.off"
                },
            ),
        )
    }
}

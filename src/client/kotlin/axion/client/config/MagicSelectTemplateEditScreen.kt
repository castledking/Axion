package axion.client.config

import axion.client.ui.FormattedNameText
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

class MagicSelectTemplateEditScreen(
    private val parent: Screen?,
    private val templateId: String,
) : Screen(Text.translatable("axion.config.magic_select.edit.title")) {
    private data class MaskRow(
        val mask: MagicSelectCustomMask,
        val contentX: Int,
        val y: Int,
        val toggleX: Int,
        val toggleWidth: Int,
    ) {
        fun containsToggle(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= toggleX &&
                mouseX < toggleX + toggleWidth &&
                mouseY >= y &&
                mouseY < y + 20
        }
    }

    private lateinit var nameField: TextFieldWidget
    private var selectedCustomMaskIds: MutableSet<String> = linkedSetOf()
    private var draftName: String = ""
    private var draftInitialized: Boolean = false
    private val rows = mutableListOf<MaskRow>()
    private val selectedBorderColor = 0xFF58D06F.toInt()
    private val idleBorderColor = 0xFF767676.toInt()

    private val template: MagicSelectTemplateConfig
        get() = AxionClientConfig.templateById(templateId)
            ?: error("Missing magic select template: $templateId")

    override fun init() {
        val currentTemplate = template
        if (!draftInitialized) {
            selectedCustomMaskIds = currentTemplate.selectedCustomMaskIds.toMutableSet()
            draftName = currentTemplate.name
            draftInitialized = true
        }
        rows.clear()

        val centerX = width / 2
        val contentWidth = 360
        val leftX = centerX - (contentWidth / 2)
        var y = 118

        nameField = TextFieldWidget(textRenderer, leftX, 88, contentWidth, 20, Text.empty())
        nameField.text = draftName
        nameField.setMaxLength(48)
        nameField.setChangedListener { draftName = it }
        addSelectableChild(nameField)
        setInitialFocus(nameField)

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("axion.config.magic_select.edit.new_custom_mask")) {
                persistDraft(currentTemplate)
                client?.setScreen(MagicSelectCustomMaskScreen(this, currentTemplate.id))
            }.dimensions(leftX, y, contentWidth, 20).build(),
        )
        y += 30

        AxionClientConfig.magicSelectCustomMasks().forEach { mask ->
            val toggleX = leftX + 46
            val toggleWidth = contentWidth - 100
            rows += MaskRow(mask = mask, contentX = leftX, y = y, toggleX = toggleX, toggleWidth = toggleWidth)
            addDrawableChild(
                ButtonWidget.builder(toggleLabel(mask.name, mask.id in selectedCustomMaskIds)) {
                    if (mask.id in selectedCustomMaskIds) {
                        selectedCustomMaskIds.remove(mask.id)
                    } else {
                        selectedCustomMaskIds.add(mask.id)
                    }
                    clearAndInit()
                }.dimensions(toggleX, y, toggleWidth, 20).build(),
            )
            addDrawableChild(
                ButtonWidget.builder(Text.translatable("axion.config.magic_select.edit.button")) {
                    persistDraft(currentTemplate)
                    client?.setScreen(MagicSelectCustomMaskScreen(this, currentTemplate.id, mask.id))
                }.dimensions(leftX + contentWidth - 50, y, 50, 20).build(),
            )
            y += 24
        }

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("axion.config.magic_select.edit.save")) {
                AxionClientConfig.updateMagicSelectTemplate(
                    currentTemplate.copy(
                        name = nameField.text.trim().ifEmpty { currentTemplate.name },
                        selectedCustomMaskIds = selectedCustomMaskIds.toSet(),
                    ),
                )
                draftInitialized = false
                close()
            }.dimensions(leftX, height - 34, 96, 20).build(),
        )

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.back")) {
                close()
            }.dimensions(centerX - 40, height - 62, 80, 20).build(),
        )

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("axion.config.magic_select.edit.delete")) {
                AxionClientConfig.deleteMagicSelectTemplate(currentTemplate.id)
                draftInitialized = false
                close()
            }.dimensions(leftX + contentWidth - 96, height - 34, 96, 20).build(),
        )
    }

    override fun close() {
        client?.setScreen(parent)
    }

    fun attachCreatedCustomMask(maskId: String) {
        selectedCustomMaskIds.add(maskId)
    }

    fun detachDeletedCustomMask(maskId: String) {
        selectedCustomMaskIds.remove(maskId)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        val centerX = width / 2
        val contentWidth = 360
        val leftX = centerX - (contentWidth / 2)

        context.drawCenteredTextWithShadow(textRenderer, title, centerX, 20, 0xFFFFFF)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.edit.description"),
            centerX,
            34,
            0xBFBFBF,
        )
        context.drawCenteredTextWithShadow(
            textRenderer,
            FormattedNameText.parse(nameField.text.ifEmpty { template.name }),
            centerX,
            58,
            0xFFFFFF,
        )

        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.edit.name"),
            leftX,
            74,
            0xFFFFFF,
        )
        nameField.render(context, mouseX, mouseY, deltaTicks)

        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.edit.masks"),
            leftX,
            104,
            0xFFFFFF,
        )

        if (rows.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("axion.config.magic_select.edit.no_masks"),
                centerX,
                148,
                0x8A8A8A,
            )
        }

        rows.forEach { row ->
            AxionClientConfig.customMaskIcons(row.mask).forEachIndexed { index, item ->
                context.drawItem(item.defaultStack, row.contentX + (index * 18), row.y + 2)
            }
            context.drawStrokedRectangle(
                row.toggleX,
                row.y,
                row.toggleWidth,
                20,
                if (row.mask.id in selectedCustomMaskIds) selectedBorderColor else idleBorderColor,
            )
        }

        rows.firstOrNull { it.containsToggle(mouseX.toDouble(), mouseY.toDouble()) }?.let { row ->
            context.drawTooltip(
                textRenderer,
                activeTemplatesTooltip(row.mask.id),
                mouseX,
                mouseY,
            )
        }
    }

    private fun persistDraft(currentTemplate: MagicSelectTemplateConfig = template) {
        AxionClientConfig.updateMagicSelectTemplate(
            currentTemplate.copy(
                name = nameField.text.trim().ifEmpty { currentTemplate.name },
                selectedCustomMaskIds = selectedCustomMaskIds.toSet(),
            ),
        )
    }

    private fun toggleLabel(name: String, enabled: Boolean): Text {
        return Text.empty()
            .append(FormattedNameText.parse(name))
            .append(Text.literal(": "))
            .append(Text.translatable(if (enabled) "axion.config.toggle.on" else "axion.config.toggle.off"))
    }

    private fun activeTemplatesTooltip(maskId: String): List<Text> {
        val activeTemplates = AxionClientConfig.magicSelectTemplates()
            .map { template ->
                if (template.id == templateId) {
                    template.copy(selectedCustomMaskIds = selectedCustomMaskIds.toSet())
                } else {
                    template
                }
            }
            .filter { maskId in it.selectedCustomMaskIds }

        if (activeTemplates.isEmpty()) {
            return listOf(
                Text.literal("Active in templates:").formatted(Formatting.GRAY),
                Text.literal("None").formatted(Formatting.DARK_GRAY),
            )
        }

        val lines = mutableListOf<Text>()
        lines += Text.literal("Active in templates:").formatted(Formatting.GRAY)
        activeTemplates.forEach { template ->
            lines += bulletLine(FormattedNameText.parse(template.name))
        }
        return lines
    }

    private fun bulletLine(content: Text): Text {
        val line: MutableText = Text.literal("• ").formatted(Formatting.GRAY)
        line.append(content)
        return line
    }
}

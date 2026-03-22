package axion.client.config

import axion.client.ui.FormattedNameText
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

class MagicSelectTemplateEditScreen(
    private val parent: Screen?,
    private val templateId: String,
) : Screen(Text.translatable("axion.config.magic_select.edit.title")) {
    private data class MaskRow(
        val mask: MagicSelectCustomMask,
        val contentX: Int,
        val y: Int,
    )

    private lateinit var nameField: TextFieldWidget
    private var selectedCustomMaskIds: MutableSet<String> = linkedSetOf()
    private var draftName: String = ""
    private var draftInitialized: Boolean = false
    private val rows = mutableListOf<MaskRow>()

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
        val contentWidth = 280
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
            rows += MaskRow(mask = mask, contentX = leftX, y = y)
            addDrawableChild(
                ButtonWidget.builder(maskStateLabel(mask)) {
                    if (mask.id in selectedCustomMaskIds) {
                        selectedCustomMaskIds.remove(mask.id)
                    } else {
                        selectedCustomMaskIds.add(mask.id)
                    }
                    clearAndInit()
                }.dimensions(leftX + contentWidth - 44, y, 44, 20).build(),
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

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        val centerX = width / 2
        val contentWidth = 280
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
            context.drawTextWithShadow(
                textRenderer,
                FormattedNameText.parse(row.mask.name),
                row.contentX + 46,
                row.y + 6,
                0xFFFFFF,
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

    private fun maskStateLabel(mask: MagicSelectCustomMask): Text {
        return Text.translatable(if (mask.id in selectedCustomMaskIds) "axion.config.toggle.on" else "axion.config.toggle.off")
    }
}

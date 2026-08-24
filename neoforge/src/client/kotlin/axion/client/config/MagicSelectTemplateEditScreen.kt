package axion.client.config

import axion.client.ui.FormattedNameText
import axion.client.ui.drawStrokedRectangleCompat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting

class MagicSelectTemplateEditScreen(
    private val parent: Screen?,
    private val templateId: String,
) : Screen(Component.translatable("axion.config.magic_select.editWorld.title")) {
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

    private lateinit var nameField: EditBox
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

        nameField = EditBox(font, leftX, 88, contentWidth, 20, Component.empty())
        nameField.value = draftName
        nameField.setMaxLength(48)
        nameField.setResponder { draftName = it }
        addWidget(nameField)
        setInitialFocus(nameField)

        addRenderableWidget(
            Button.builder(Component.translatable("axion.config.magic_select.editWorld.new_custom_mask")) {
                persistDraft(currentTemplate)
                minecraft?.setScreen(MagicSelectCustomMaskScreen(this, currentTemplate.id))
            }.bounds(leftX, y, contentWidth, 20).build(),
        )
        y += 30

        AxionClientConfig.magicSelectCustomMasks().forEach { mask ->
            val toggleX = leftX + 46
            val toggleWidth = contentWidth - 100
            rows += MaskRow(mask = mask, contentX = leftX, y = y, toggleX = toggleX, toggleWidth = toggleWidth)
            addRenderableWidget(
                Button.builder(toggleLabel(mask.name, mask.id in selectedCustomMaskIds)) {
                    if (mask.id in selectedCustomMaskIds) {
                        selectedCustomMaskIds.remove(mask.id)
                    } else {
                        selectedCustomMaskIds.add(mask.id)
                    }
                    rebuildWidgets()
                }.bounds(toggleX, y, toggleWidth, 20).build(),
            )
            addRenderableWidget(
                Button.builder(Component.translatable("axion.config.magic_select.editWorld.button")) {
                    persistDraft(currentTemplate)
                    minecraft?.setScreen(MagicSelectCustomMaskScreen(this, currentTemplate.id, mask.id))
                }.bounds(leftX + contentWidth - 50, y, 50, 20).build(),
            )
            y += 24
        }

        addRenderableWidget(
            Button.builder(Component.translatable("axion.config.magic_select.editWorld.save")) {
                AxionClientConfig.updateMagicSelectTemplate(
                    currentTemplate.copy(
                        name = nameField.value.trim().ifEmpty { currentTemplate.name },
                        selectedCustomMaskIds = selectedCustomMaskIds.toSet(),
                    ),
                )
                draftInitialized = false
                onClose()
            }.bounds(leftX, height - 34, 96, 20).build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) {
                onClose()
            }.bounds(centerX - 40, height - 62, 80, 20).build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("axion.config.magic_select.editWorld.delete")) {
                AxionClientConfig.deleteMagicSelectTemplate(currentTemplate.id)
                draftInitialized = false
                onClose()
            }.bounds(leftX + contentWidth - 96, height - 34, 96, 20).build(),
        )
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    fun attachCreatedCustomMask(maskId: String) {
        selectedCustomMaskIds.add(maskId)
    }

    fun detachDeletedCustomMask(maskId: String) {
        selectedCustomMaskIds.remove(maskId)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        val centerX = width / 2
        val contentWidth = 360
        val leftX = centerX - (contentWidth / 2)

        context.drawCenteredString(font, title, centerX, 20, 0xFFFFFF)
        context.drawCenteredString(
            font,
            Component.translatable("axion.config.magic_select.editWorld.description"),
            centerX,
            34,
            0xBFBFBF,
        )
        context.drawCenteredString(
            font,
            FormattedNameText.parse(nameField.value.ifEmpty { template.name }),
            centerX,
            58,
            0xFFFFFF,
        )

        context.drawString(
            font,
            Component.translatable("axion.config.magic_select.editWorld.name"),
            leftX,
            74,
            0xFFFFFF,
        )
        nameField.render(context, mouseX, mouseY, deltaTicks)

        context.drawString(
            font,
            Component.translatable("axion.config.magic_select.editWorld.masks"),
            leftX,
            104,
            0xFFFFFF,
        )

        if (rows.isEmpty()) {
            context.drawCenteredString(
                font,
                Component.translatable("axion.config.magic_select.editWorld.no_masks"),
                centerX,
                148,
                0x8A8A8A,
            )
        }

        rows.forEach { row ->
            AxionClientConfig.customMaskIcons(row.mask).forEachIndexed { index, item ->
                context.renderItem(item.defaultInstance, row.contentX + (index * 18), row.y + 2)
            }
            context.drawStrokedRectangleCompat(
                row.toggleX,
                row.y,
                row.toggleWidth,
                20,
                if (row.mask.id in selectedCustomMaskIds) selectedBorderColor else idleBorderColor,
            )
        }

        rows.firstOrNull { it.containsToggle(mouseX.toDouble(), mouseY.toDouble()) }?.let { row ->
            context.setComponentTooltipForNextFrame(
                font,
                activeTemplatesTooltip(row.mask.id),
                mouseX,
                mouseY,
            )
        }
    }

    private fun persistDraft(currentTemplate: MagicSelectTemplateConfig = template) {
        AxionClientConfig.updateMagicSelectTemplate(
            currentTemplate.copy(
                name = nameField.value.trim().ifEmpty { currentTemplate.name },
                selectedCustomMaskIds = selectedCustomMaskIds.toSet(),
            ),
        )
    }

    private fun toggleLabel(name: String, enabled: Boolean): Component {
        return Component.empty()
            .append(FormattedNameText.parse(name))
            .append(Component.literal(": "))
            .append(Component.translatable(if (enabled) "axion.config.toggle.on" else "axion.config.toggle.off"))
    }

    private fun activeTemplatesTooltip(maskId: String): List<Component> {
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
                Component.literal("Active in templates:").withStyle(ChatFormatting.GRAY),
                Component.literal("None").withStyle(ChatFormatting.DARK_GRAY),
            )
        }

        val lines = mutableListOf<Component>()
        lines += Component.literal("Active in templates:").withStyle(ChatFormatting.GRAY)
        activeTemplates.forEach { template ->
            lines += bulletLine(FormattedNameText.parse(template.name))
        }
        return lines
    }

    private fun bulletLine(content: Component): Component {
        val line: MutableComponent = Component.literal("• ").withStyle(ChatFormatting.GRAY)
        line.append(content)
        return line
    }
}

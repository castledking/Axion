package axion.client.config

import axion.client.ui.FormattedNameText
import net.minecraft.block.Block
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.ceil

class MagicSelectCustomMaskScreen(
    private val parent: Screen?,
    private val templateId: String,
    private val maskId: String? = null,
) : Screen(Text.empty()) {
    private data class BlockEntry(
        val block: Block,
        val id: Identifier,
        val label: String,
    )

    private data class TileBounds(
        val entry: BlockEntry,
        val x: Int,
        val y: Int,
        val size: Int,
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size
        }
    }

    private lateinit var nameField: TextFieldWidget
    private lateinit var searchField: TextFieldWidget
    private lateinit var prevPageButton: ButtonWidget
    private lateinit var nextPageButton: ButtonWidget
    private var selectedRuleIds: MutableSet<String> = linkedSetOf()
    private var selectedBlockIds: MutableSet<String> = linkedSetOf()
    private var excludedBlockIds: MutableSet<String> = linkedSetOf()
    private var draftName: String = ""
    private var draftInitialized: Boolean = false
    private var page: Int = 0
    private var searchQuery: String = ""

    private val allBlocks: List<BlockEntry> by lazy {
        Registries.BLOCK.mapNotNull { block ->
            val item = block.asItem()
            if (item == Items.AIR) {
                null
            } else {
                BlockEntry(
                    block = block,
                    id = Registries.BLOCK.getId(block),
                    label = item.name.string.lowercase(),
                )
            }
        }.sortedBy { it.label }
    }

    private val existingMask: MagicSelectCustomMask?
        get() = maskId?.let(AxionClientConfig::customMaskById)

    override fun init() {
        val centerX = width / 2
        val topY = 40
        if (!draftInitialized) {
            existingMask?.let { mask ->
                draftName = mask.name
                selectedRuleIds = mask.ruleIds.toMutableSet()
                selectedBlockIds = mask.customBlockIds.toMutableSet()
                excludedBlockIds = mask.excludedBlockIds.toMutableSet()
            }
            draftInitialized = true
        }

        nameField = TextFieldWidget(textRenderer, centerX - 130, topY, 260, 20, Text.empty())
        nameField.setMaxLength(48)
        nameField.text = draftName
        nameField.setChangedListener { draftName = it }
        addSelectableChild(nameField)
        setInitialFocus(nameField)

        searchField = TextFieldWidget(textRenderer, centerX - 130, 158, 260, 20, Text.empty())
        searchField.text = searchQuery
        searchField.setChangedListener {
            searchQuery = it
            page = 0
            updatePagingButtons()
        }
        addSelectableChild(searchField)

        var ruleX = centerX - 130
        var ruleY = 92
        MagicSelectRule.customMaskRules().forEachIndexed { index, rule ->
            addDrawableChild(
                ButtonWidget.builder(ruleToggleLabel(rule)) {
                    if (rule.id in selectedRuleIds) {
                        selectedRuleIds.remove(rule.id)
                    } else {
                        selectedRuleIds.add(rule.id)
                    }
                    clearAndInit()
                }.dimensions(ruleX, ruleY, 84, 20).build(),
            )
            ruleX += 88
            if ((index + 1) % 3 == 0) {
                ruleX = centerX - 130
                ruleY += 24
            }
        }

        prevPageButton = addDrawableChild(
            ButtonWidget.builder(Text.translatable("axion.config.magic_select.blocks.prev")) {
                if (page > 0) {
                    page -= 1
                    clearAndInit()
                }
            }.dimensions(centerX - 130, height - 34, 60, 20).build().apply {
                active = page > 0
            },
        )

        addDrawableChild(
            ButtonWidget.builder(confirmButtonText()) {
                val currentMask = existingMask
                if (currentMask != null) {
                    AxionClientConfig.updateMagicSelectCustomMask(
                        currentMask.copy(
                            name = nameField.text,
                            ruleIds = selectedRuleIds.toSet(),
                            customBlockIds = selectedBlockIds.toSet(),
                            excludedBlockIds = excludedBlockIds.toSet(),
                        ),
                    )
                } else {
                    val customMaskId = AxionClientConfig.createMagicSelectCustomMask(
                        name = nameField.text,
                        ruleIds = selectedRuleIds.toSet(),
                        customBlockIds = selectedBlockIds.toSet(),
                        excludedBlockIds = excludedBlockIds.toSet(),
                    )
                    AxionClientConfig.templateById(templateId)?.let { template ->
                        AxionClientConfig.setMagicSelectTemplateSelectedCustomMasks(
                            templateId,
                            template.selectedCustomMaskIds + customMaskId,
                        )
                    }
                    if (parent is MagicSelectTemplateEditScreen) {
                        parent.attachCreatedCustomMask(customMaskId)
                    }
                }
                draftInitialized = false
                close()
            }.dimensions(centerX - 64, height - 34, 128, 20).build().apply {
                active = selectedRuleIds.isNotEmpty() || selectedBlockIds.isNotEmpty()
            },
        )

        nextPageButton = addDrawableChild(
            ButtonWidget.builder(Text.translatable("axion.config.magic_select.blocks.next")) {
                if (page + 1 < pageCount()) {
                    page += 1
                    clearAndInit()
                }
            }.dimensions(centerX + 70, height - 34, 60, 20).build().apply {
                active = page + 1 < pageCount()
            },
        )

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("axion.config.magic_select.custom_mask.clear")) {
                selectedRuleIds.clear()
                selectedBlockIds.clear()
                excludedBlockIds.clear()
                clearAndInit()
            }.dimensions(centerX - 186, height - 62, 120, 20).build(),
        )

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.cancel")) {
                close()
            }.dimensions(centerX - 60, height - 62, 120, 20).build(),
        )

        if (existingMask != null) {
            val currentMask = existingMask ?: return
            addDrawableChild(
                ButtonWidget.builder(Text.translatable("axion.config.magic_select.custom_mask.delete")) {
                    AxionClientConfig.deleteMagicSelectCustomMask(currentMask.id)
                    if (parent is MagicSelectTemplateEditScreen) {
                        parent.detachDeletedCustomMask(currentMask.id)
                    }
                    draftInitialized = false
                    close()
                }.dimensions(centerX + 70, height - 62, 120, 20).build(),
            )
        }

        updatePagingButtons()
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (super.mouseClicked(click, doubled)) {
            return true
        }

        tileBounds().firstOrNull { it.contains(click.x(), click.y()) }?.let { tile ->
            val blockId = tile.entry.id.toString()
            val selectedByRule = selectedRuleIds
                .mapNotNull(MagicSelectRule::fromId)
                .any { rule -> rule.includes(tile.entry.block.defaultState) }
            val selectedByCustom = blockId in selectedBlockIds
            val selectedByEffectiveState = isBlockSelected(blockId, tile.entry.block.defaultState)

            if (selectedByEffectiveState) {
                if (selectedByCustom) {
                    selectedBlockIds.remove(blockId)
                }
                if (selectedByRule) {
                    excludedBlockIds.add(blockId)
                }
            } else if (selectedByRule && blockId in excludedBlockIds) {
                excludedBlockIds.remove(blockId)
            } else {
                excludedBlockIds.remove(blockId)
                selectedBlockIds.add(blockId)
            }
            clearAndInit()
            return true
        }

        return false
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        val centerX = width / 2
        val leftX = centerX - 130

        context.drawCenteredTextWithShadow(textRenderer, screenTitle(), centerX, 18, 0xFFFFFF)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(descriptionKey()),
            centerX,
            30,
            0xBFBFBF,
        )
        context.drawCenteredTextWithShadow(
            textRenderer,
            FormattedNameText.parse(nameField.text.ifEmpty { "New Custom Mask" }),
            centerX,
            62,
            0xFFFFFF,
        )

        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.custom_mask.name"),
            leftX,
            26,
            0xFFFFFF,
        )
        nameField.render(context, mouseX, mouseY, deltaTicks)

        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.custom_mask.rules"),
            leftX,
            78,
            0xFFFFFF,
        )

        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.custom_mask.blocks"),
            leftX,
            136,
            0xFFFFFF,
        )
        searchField.render(context, mouseX, mouseY, deltaTicks)

        val hoveredTile = tileBounds().firstOrNull { it.contains(mouseX.toDouble(), mouseY.toDouble()) }
        tileBounds().forEach { tile ->
            val selected = isBlockSelected(tile.entry.id.toString(), tile.entry.block.defaultState)
            context.fill(tile.x, tile.y, tile.x + tile.size, tile.y + tile.size, 0xAA1A1A1A.toInt())
            context.drawStrokedRectangle(
                tile.x,
                tile.y,
                tile.size,
                tile.size,
                if (selected) 0xFF58D06F.toInt() else 0xFF767676.toInt(),
            )
            context.drawItem(tile.entry.block.asItem().defaultStack, tile.x + 3, tile.y + 3)
        }

        hoveredTile?.let { tile ->
            context.drawTooltip(textRenderer, tile.entry.block.asItem().name, mouseX, mouseY)
        }

        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.blocks.page", page + 1, pageCount().coerceAtLeast(1)),
            centerX,
            height - 48,
            0x8A8A8A,
        )
    }

    private fun filteredBlocks(): List<BlockEntry> {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            return allBlocks
        }
        return allBlocks.filter { entry ->
            entry.label.contains(query) || entry.id.toString().contains(query)
        }
    }

    private fun pageCount(): Int {
        return ceil(filteredBlocks().size / TILES_PER_PAGE.toDouble()).toInt().coerceAtLeast(1)
    }

    private fun updatePagingButtons() {
        if (::prevPageButton.isInitialized) {
            prevPageButton.active = page > 0
        }
        if (::nextPageButton.isInitialized) {
            nextPageButton.active = page + 1 < pageCount()
        }
    }

    private fun tileBounds(): List<TileBounds> {
        val filtered = filteredBlocks()
        val start = (page * TILES_PER_PAGE).coerceAtMost(filtered.size)
        val end = (start + TILES_PER_PAGE).coerceAtMost(filtered.size)
        val visible = filtered.subList(start, end)
        val totalWidth = (COLUMNS * TILE_SIZE) + ((COLUMNS - 1) * TILE_GAP)
        val startX = (width / 2) - (totalWidth / 2)
        val startY = 218

        return visible.mapIndexed { index, entry ->
            val column = index % COLUMNS
            val row = index / COLUMNS
            TileBounds(
                entry = entry,
                x = startX + (column * (TILE_SIZE + TILE_GAP)),
                y = startY + (row * (TILE_SIZE + TILE_GAP)),
                size = TILE_SIZE,
            )
        }
    }

    private fun ruleToggleLabel(rule: MagicSelectRule): Text {
        val statusKey = when {
            rule.id !in selectedRuleIds -> "axion.config.toggle.off"
            isRuleCustomized(rule) -> "axion.config.magic_select.custom_mask.custom"
            else -> "axion.config.toggle.on"
        }
        return Text.translatable("axion.config.magic_select.edit.rule_button", Text.of(rule.displayName), Text.translatable(statusKey))
    }

    private fun isRuleCustomized(rule: MagicSelectRule): Boolean {
        if (rule.id !in selectedRuleIds) {
            return false
        }
        return excludedBlockIds.any { blockId ->
            val block = Identifier.tryParse(blockId)?.let(Registries.BLOCK::get) ?: return@any false
            rule.includes(block.defaultState)
        }
    }

    private fun isBlockSelected(blockId: String, blockState: net.minecraft.block.BlockState): Boolean {
        if (blockId in excludedBlockIds) {
            return false
        }
        if (blockId in selectedBlockIds) {
            return true
        }
        return selectedRuleIds
            .mapNotNull(MagicSelectRule::fromId)
            .any { rule -> rule.includes(blockState) }
    }

    private fun confirmButtonText(): Text {
        return Text.translatable(
            if (existingMask == null) {
                "axion.config.magic_select.custom_mask.confirm"
            } else {
                "axion.config.magic_select.custom_mask.save"
            },
        )
    }

    private fun descriptionKey(): String {
        return if (existingMask == null) {
            "axion.config.magic_select.custom_mask.description"
        } else {
            "axion.config.magic_select.custom_mask.edit_description"
        }
    }

    private fun screenTitle(): Text {
        return Text.translatable(
            if (existingMask == null) {
                "axion.config.magic_select.custom_mask.title"
            } else {
                "axion.config.magic_select.custom_mask.edit_title"
            },
        )
    }

    companion object {
        private const val COLUMNS = 9
        private const val ROWS = 4
        private const val TILE_SIZE = 22
        private const val TILE_GAP = 4
        private const val TILES_PER_PAGE = COLUMNS * ROWS
    }
}

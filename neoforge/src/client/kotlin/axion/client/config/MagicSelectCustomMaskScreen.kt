package axion.client.config

import axion.client.ui.FormattedNameText
import axion.client.ui.drawStrokedRectangleCompat
import axion.common.compat.VersionCompat
import net.minecraft.world.level.block.Block
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.world.item.Items
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import kotlin.math.ceil

class MagicSelectCustomMaskScreen(
    private val parent: Screen?,
    private val templateId: String,
    private val maskId: String? = null,
) : Screen(Component.empty()) {
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

    private lateinit var nameField: EditBox
    private lateinit var searchField: EditBox
    private lateinit var prevPageButton: Button
    private lateinit var nextPageButton: Button
    private var selectedRuleIds: MutableSet<String> = linkedSetOf()
    private var selectedBlockIds: MutableSet<String> = linkedSetOf()
    private var excludedBlockIds: MutableSet<String> = linkedSetOf()
    private var draftName: String = ""
    private var draftInitialized: Boolean = false
    private var page: Int = 0
    private var searchQuery: String = ""

    private val allBlocks: List<BlockEntry> by lazy {
        BuiltInRegistries.BLOCK.mapNotNull { block ->
            val item = block.asItem()
            if (item == Items.AIR) {
                null
            } else {
                BlockEntry(
                    block = block,
                    id = VersionCompat.INSTANCE.getBlockId(block),
                    label = item.name.string.lowercase(),
                )
            }
        }.sortedBy { it.label }
    }

    private val existingMask: MagicSelectCustomMask?
        get() = maskId?.let(AxionClientConfig::customMaskById)

    override fun init() {
        val centerX = width / 2
        val contentWidth = 360
        val leftX = centerX - (contentWidth / 2)
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

        nameField = EditBox(font, leftX, topY, contentWidth, 20, Component.empty())
        nameField.setMaxLength(48)
        nameField.value = draftName
        nameField.setResponder { draftName = it }
        addWidget(nameField)
        setInitialFocus(nameField)

        searchField = EditBox(font, leftX, 272, contentWidth, 20, Component.empty())
        searchField.value = searchQuery
        searchField.setResponder {
            searchQuery = it
            page = 0
            updatePagingButtons()
        }
        addWidget(searchField)

        var ruleX = leftX
        var ruleY = 92
        MagicSelectRule.customMaskRules().forEachIndexed { index, rule ->
            addRenderableWidget(
                Button.builder(ruleToggleLabel(rule)) {
                    if (rule.id in selectedRuleIds) {
                        selectedRuleIds.remove(rule.id)
                    } else {
                        selectedRuleIds.add(rule.id)
                    }
                    rebuildWidgets()
                }.bounds(ruleX, ruleY, 84, 20).build(),
            )
            ruleX += 92
            if ((index + 1) % 4 == 0) {
                ruleX = leftX
                ruleY += 24
            }
        }

        prevPageButton = addRenderableWidget(
            Button.builder(Component.translatable("axion.config.magic_select.blocks.prev")) {
                if (page > 0) {
                    page -= 1
                    rebuildWidgets()
                }
            }.bounds(centerX - 130, height - 34, 60, 20).build().apply {
                active = page > 0
            },
        )

        addRenderableWidget(
            Button.builder(confirmButtonText()) {
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
                onClose()
            }.bounds(centerX - 64, height - 34, 128, 20).build().apply {
                active = selectedRuleIds.isNotEmpty() || selectedBlockIds.isNotEmpty()
            },
        )

        nextPageButton = addRenderableWidget(
            Button.builder(Component.translatable("axion.config.magic_select.blocks.next")) {
                if (page + 1 < pageCount()) {
                    page += 1
                    rebuildWidgets()
                }
            }.bounds(centerX + 70, height - 34, 60, 20).build().apply {
                active = page + 1 < pageCount()
            },
        )

        addRenderableWidget(
            Button.builder(Component.translatable("axion.config.magic_select.custom_mask.clear")) {
                selectedRuleIds.clear()
                selectedBlockIds.clear()
                excludedBlockIds.clear()
                rebuildWidgets()
            }.bounds(centerX - 186, height - 62, 120, 20).build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("gui.cancel")) {
                onClose()
            }.bounds(centerX - 60, height - 62, 120, 20).build(),
        )

        if (existingMask != null) {
            val currentMask = existingMask ?: return
            addRenderableWidget(
                Button.builder(Component.translatable("axion.config.magic_select.custom_mask.delete")) {
                    AxionClientConfig.deleteMagicSelectCustomMask(currentMask.id)
                    if (parent is MagicSelectTemplateEditScreen) {
                        parent.detachDeletedCustomMask(currentMask.id)
                    }
                    draftInitialized = false
                    onClose()
                }.bounds(centerX + 70, height - 62, 120, 20).build(),
            )
        }

        tileBounds().forEach { tile ->
            addRenderableWidget(
                Button.builder(Component.empty()) {
                    toggleTile(tile.entry)
                    rebuildWidgets()
                }.bounds(tile.x, tile.y, tile.size, tile.size).build().apply {
                    setAlpha(0f)
                },
            )
        }

        updatePagingButtons()
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        val centerX = width / 2
        val contentWidth = 360
        val leftX = centerX - (contentWidth / 2)

        context.drawCenteredString(font, screenTitle(), centerX, 18, 0xFFFFFF)
        context.drawCenteredString(
            font,
            Component.translatable(descriptionKey()),
            centerX,
            30,
            0xBFBFBF,
        )
        context.drawCenteredString(
            font,
            FormattedNameText.parse(nameField.text.ifEmpty { "New Custom Mask" }),
            centerX,
            62,
            0xFFFFFF,
        )

        context.drawString(
            font,
            Component.translatable("axion.config.magic_select.custom_mask.name"),
            leftX,
            26,
            0xFFFFFF,
        )
        nameField.render(context, mouseX, mouseY, deltaTicks)

        context.drawString(
            font,
            Component.translatable("axion.config.magic_select.custom_mask.rules"),
            leftX,
            78,
            0xFFFFFF,
        )

        context.drawString(
            font,
            Component.translatable("axion.config.magic_select.custom_mask.blocks"),
            leftX,
            250,
            0xFFFFFF,
        )
        searchField.render(context, mouseX, mouseY, deltaTicks)

        val hoveredTile = tileBounds().firstOrNull { it.contains(mouseX.toDouble(), mouseY.toDouble()) }
        tileBounds().forEach { tile ->
            val selected = isBlockSelected(tile.entry.id.toString(), tile.entry.block.defaultBlockState())
            context.fill(tile.x, tile.y, tile.x + tile.size, tile.y + tile.size, 0xAA1A1A1A.toInt())
            context.drawStrokedRectangleCompat(
                tile.x,
                tile.y,
                tile.size,
                tile.size,
                if (selected) 0xFF58D06F.toInt() else 0xFF767676.toInt(),
            )
            context.drawItem(tile.entry.block.asItem().defaultStack, tile.x + 3, tile.y + 3)
        }

        hoveredTile?.let { tile ->
            context.drawTooltip(font, tile.entry.block.asItem().name, mouseX, mouseY)
        }

        context.drawCenteredString(
            font,
            Component.translatable("axion.config.magic_select.blocks.page", page + 1, pageCount().coerceAtLeast(1)),
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

    private fun toggleTile(entry: BlockEntry) {
        val blockId = entry.id.toString()
        val selectedByRule = selectedRuleIds
            .mapNotNull(MagicSelectRule::fromId)
            .any { rule -> rule.contains(entry.block.defaultBlockState()) }
        val selectedByCustom = blockId in selectedBlockIds
        val selectedByEffectiveState = isBlockSelected(blockId, entry.block.defaultBlockState())

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
        val startY = 302

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

    private fun ruleToggleLabel(rule: MagicSelectRule): Component {
        val statusKey = when {
            rule.id !in selectedRuleIds -> "axion.config.toggle.off"
            isRuleCustomized(rule) -> "axion.config.magic_select.custom_mask.custom"
            else -> "axion.config.toggle.on"
        }
        return Component.translatable("axion.config.magic_select.editWorld.rule_button", Component.literal(rule.displayName), Component.translatable(statusKey))
    }

    private fun isRuleCustomized(rule: MagicSelectRule): Boolean {
        if (rule.id !in selectedRuleIds) {
            return false
        }
        return excludedBlockIds.any { blockId ->
            val block = Identifier.tryParse(blockId)?.let(VersionCompat.INSTANCE::getBlock) ?: return@any false
            rule.contains(block.defaultBlockState())
        }
    }

    private fun isBlockSelected(blockId: String, blockState: net.minecraft.world.level.block.state.BlockState): Boolean {
        if (blockId in excludedBlockIds) {
            return false
        }
        if (blockId in selectedBlockIds) {
            return true
        }
        return selectedRuleIds
            .mapNotNull(MagicSelectRule::fromId)
            .any { rule -> rule.contains(blockState) }
    }

    private fun confirmButtonText(): Component {
        return Component.translatable(
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

    private fun screenTitle(): Component {
        return Component.translatable(
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

package axion.client.config

import axion.client.ui.drawStrokedRectangleCompat
import net.minecraft.block.Block
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.ceil

class MagicSelectBlockPickerScreen(
    private val parent: Screen?,
    private val templateId: String,
) : Screen(Text.translatable("axion.config.magic_select.blocks.title")) {
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

    private lateinit var searchField: TextFieldWidget
    private lateinit var prevPageButton: ButtonWidget
    private lateinit var nextPageButton: ButtonWidget
    private var selectedBlockIds: MutableSet<String> = linkedSetOf()
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

    private val template: MagicSelectTemplateConfig
        get() = AxionClientConfig.templateById(templateId)
            ?: error("Missing magic select template: $templateId")

    override fun init() {
        val centerX = width / 2
        val topY = 40

        if (selectedBlockIds.isEmpty()) {
            selectedBlockIds = template.customBlockIds.toMutableSet()
        }

        searchField = TextFieldWidget(textRenderer, centerX - 130, topY, 260, 20, Text.empty())
        searchField.text = searchQuery
        searchField.setChangedListener {
            searchQuery = it
            page = 0
            updatePagingButtons()
        }
        addSelectableChild(searchField)
        setInitialFocus(searchField)

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
            ButtonWidget.builder(Text.translatable("axion.config.magic_select.blocks.save")) {
                AxionClientConfig.setMagicSelectTemplateCustomBlocks(template.id, selectedBlockIds.toSet())
                close()
            }.dimensions(centerX - 64, height - 34, 128, 20).build(),
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

        tileBounds().forEach { tile ->
            addDrawableChild(
                ButtonWidget.builder(Text.empty()) {
                    toggleTile(tile.entry)
                }.dimensions(tile.x, tile.y, tile.size, tile.size).build().apply {
                    setAlpha(0f)
                },
            )
        }

        updatePagingButtons()
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        val centerX = width / 2
        context.drawCenteredTextWithShadow(textRenderer, title, centerX, 18, 0xFFFFFF)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("axion.config.magic_select.blocks.description"),
            centerX,
            30,
            0xBFBFBF,
        )
        searchField.render(context, mouseX, mouseY, deltaTicks)

        val hoveredTile = tileBounds().firstOrNull { it.contains(mouseX.toDouble(), mouseY.toDouble()) }
        tileBounds().forEach { tile ->
            val selected = tile.entry.id.toString() in selectedBlockIds
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
        val startY = 88

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

    private fun toggleTile(entry: BlockEntry) {
        val blockId = entry.id.toString()
        if (blockId in selectedBlockIds) {
            selectedBlockIds.remove(blockId)
        } else {
            selectedBlockIds.add(blockId)
        }
    }

    companion object {
        private const val COLUMNS = 9
        private const val ROWS = 5
        private const val TILE_SIZE = 22
        private const val TILE_GAP = 4
        private const val TILES_PER_PAGE = COLUMNS * ROWS
    }
}

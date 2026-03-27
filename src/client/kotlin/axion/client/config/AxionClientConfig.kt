package axion.client.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import java.nio.file.Files
import java.nio.file.Path

object AxionClientConfig {
    private const val MIN_SAVED_HOTBAR_COUNT: Int = 9
    private const val HOTBAR_SLOT_COUNT: Int = 9
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path: Path = FabricLoader.getInstance().configDir.resolve("axion-client.json")

    private var data: Data = Data.default()

    fun initialize() {
        data = load()
        save()
    }

    fun isMacOs(): Boolean {
        return System.getProperty("os.name")
            ?.lowercase()
            ?.contains("mac") == true
    }

    fun useCommandModifierOnMac(): Boolean = isMacOs() && data.useCommandModifierOnMac

    fun setUseCommandModifierOnMac(enabled: Boolean) {
        data = data.copy(useCommandModifierOnMac = isMacOs() && enabled)
        save()
    }

    fun savedHotbars(): List<SavedHotbarConfig> = data.savedHotbars

    fun savedHotbar(index: Int): SavedHotbarConfig? = data.savedHotbars.getOrNull(index)

    fun ensureSavedHotbarCapacity(requiredCount: Int) {
        if (requiredCount <= data.savedHotbars.size) {
            return
        }

        data = data.copy(
            savedHotbars = data.savedHotbars + List(requiredCount - data.savedHotbars.size) { SavedHotbarConfig.empty() },
        )
        save()
    }

    fun activeSavedHotbarIndex(): Int = data.activeSavedHotbarIndex

    fun setActiveSavedHotbarIndex(index: Int) {
        ensureSavedHotbarCapacity(index + 1)
        val normalizedIndex = index.coerceIn(0, data.savedHotbars.lastIndex)
        data = data.copy(activeSavedHotbarIndex = normalizedIndex)
        save()
    }

    fun updateSavedHotbar(index: Int, hotbar: SavedHotbarConfig) {
        ensureSavedHotbarCapacity(index + 1)

        data = data.copy(
            savedHotbars = data.savedHotbars.mapIndexed { existingIndex, existing ->
                if (existingIndex == index) sanitizeSavedHotbar(hotbar) else existing
            },
        )
        save()
    }

    fun magicSelectTemplates(): List<MagicSelectTemplateConfig> = data.magicSelectTemplates

    fun enabledMagicSelectTemplates(): List<MagicSelectTemplateConfig> = data.magicSelectTemplates.filter { it.enabled }

    fun templateById(id: String): MagicSelectTemplateConfig? = data.magicSelectTemplates.firstOrNull { it.id == id }

    fun magicSelectCustomMasks(): List<MagicSelectCustomMask> = data.magicSelectCustomMasks

    fun customMaskById(id: String): MagicSelectCustomMask? = data.magicSelectCustomMasks.firstOrNull { it.id == id }

    fun updateMagicSelectTemplate(template: MagicSelectTemplateConfig) {
        data = data.copy(
            magicSelectTemplates = data.magicSelectTemplates.map { existing ->
                if (existing.id == template.id) template else existing
            },
        )
        save()
    }

    fun setMagicSelectTemplateEnabled(templateId: String, enabled: Boolean) {
        templateById(templateId)?.let { template ->
            updateMagicSelectTemplate(template.copy(enabled = enabled))
        }
    }

    fun disableAllMagicSelectTemplates() {
        data = data.copy(
            magicSelectTemplates = data.magicSelectTemplates.map { it.copy(enabled = false) },
        )
        save()
    }

    fun setMagicSelectTemplateSelectedCustomMasks(templateId: String, selectedCustomMaskIds: Set<String>) {
        templateById(templateId)?.let { template ->
            updateMagicSelectTemplate(template.copy(selectedCustomMaskIds = selectedCustomMaskIds))
        }
    }

    fun setMagicSelectTemplateCustomBlocks(templateId: String, customBlockIds: Set<String>) {
        templateById(templateId)?.let { template ->
            updateMagicSelectTemplate(template.copy(customBlockIds = customBlockIds))
        }
    }

    fun addMagicSelectTemplate(): String {
        val nextIndex = data.nextMagicTemplateIndex
        val template = MagicSelectTemplateConfig(
            id = "template_$nextIndex",
            name = "New Template $nextIndex",
            enabled = false,
            ruleIds = emptySet(),
        )
        data = data.copy(
            nextMagicTemplateIndex = nextIndex + 1,
            magicSelectTemplates = data.magicSelectTemplates + template,
        )
        save()
        return template.id
    }

    fun createMagicSelectCustomMask(
        name: String,
        ruleIds: Set<String>,
        customBlockIds: Set<String>,
        excludedBlockIds: Set<String> = emptySet(),
    ): String {
        val nextIndex = data.nextMagicCustomMaskIndex
        val candidateMask = MagicSelectCustomMask(
            id = "custom_mask_$nextIndex",
            name = name.trim().ifEmpty { "New Mask $nextIndex" },
            ruleIds = ruleIds,
            customBlockIds = customBlockIds,
            excludedBlockIds = excludedBlockIds,
        )
        val mask = sanitizeCustomMask(candidateMask)
            ?: error("Custom mask must contain at least one rule or block")
        data = data.copy(
            nextMagicCustomMaskIndex = nextIndex + 1,
            magicSelectCustomMasks = data.magicSelectCustomMasks + mask,
        )
        save()
        return mask.id
    }

    fun updateMagicSelectCustomMask(mask: MagicSelectCustomMask) {
        val sanitizedMask = sanitizeCustomMask(mask)
            ?: error("Custom mask must contain at least one rule or block")
        data = data.copy(
            magicSelectCustomMasks = data.magicSelectCustomMasks.map { existing ->
                if (existing.id == sanitizedMask.id) sanitizedMask else existing
            },
        )
        save()
    }

    fun deleteMagicSelectCustomMask(maskId: String) {
        data = data.copy(
            magicSelectCustomMasks = data.magicSelectCustomMasks.filterNot { it.id == maskId },
            magicSelectTemplates = data.magicSelectTemplates.map { template ->
                template.copy(selectedCustomMaskIds = template.selectedCustomMaskIds - maskId)
            },
        )
        save()
    }

    fun deleteMagicSelectTemplate(templateId: String) {
        data = data.copy(
            magicSelectTemplates = data.magicSelectTemplates.filterNot { it.id == templateId },
        )
        save()
    }

    fun magicSelectTemplateSummary(): String {
        val enabledNames = enabledMagicSelectTemplates().map { it.name }
        return when {
            enabledNames.isEmpty() -> "Same Block"
            else -> enabledNames.joinToString(", ")
        }
    }

    fun templateIcons(template: MagicSelectTemplateConfig): List<Item> {
        val ruleIcons = buildList {
            addAll(template.rules().flatMap { it.icons })
            addAll(
                template.selectedCustomMaskIds
                    .mapNotNull(::customMaskById)
                    .flatMap(::customMaskIcons),
            )
        }.distinct()
        return when {
            ruleIcons.isEmpty() -> listOf(Items.NAME_TAG)
            ruleIcons.size == 1 -> ruleIcons
            else -> ruleIcons.take(2)
        }
    }

    fun customMaskIcons(mask: MagicSelectCustomMask): List<Item> {
        val ruleIcons = mask.rules().flatMap { it.icons }.distinct()
        if (ruleIcons.isNotEmpty()) {
            return ruleIcons.take(2)
        }

        val blockIcons = mask.customBlockIds.mapNotNull { blockId ->
            Identifier.tryParse(blockId)?.let(Registries.BLOCK::get)
                ?.asItem()
                ?.takeIf { it != Items.AIR }
        }.distinct()

        return when {
            blockIcons.isEmpty() -> listOf(Items.NAME_TAG)
            blockIcons.size == 1 -> blockIcons
            else -> blockIcons.take(2)
        }
    }

    private fun load(): Data {
        return runCatching {
            if (!Files.exists(path)) {
                return@runCatching Data.default()
            }
            Files.newBufferedReader(path).use { reader ->
                val fileData = gson.fromJson(reader, FileData::class.java) ?: return@use Data.default()
                val defaults = Data.default()
                val loadedCustomMasks = fileData.magicSelectCustomMasks
                    ?.mapNotNull(::sanitizeCustomMask)
                    ?: defaults.magicSelectCustomMasks
                val validCustomMaskIds = loadedCustomMasks.map { it.id }.toSet()
                val loadedTemplates = fileData.magicSelectTemplates
                    ?.mapNotNull { sanitizeTemplate(it, validCustomMaskIds) }
                    ?.takeIf { it.isNotEmpty() }
                    ?: defaults.magicSelectTemplates

                Data(
                    useCommandModifierOnMac = fileData.useCommandModifierOnMac ?: defaults.useCommandModifierOnMac,
                    activeSavedHotbarIndex = (fileData.activeSavedHotbarIndex ?: defaults.activeSavedHotbarIndex)
                        .coerceIn(0, sanitizeSavedHotbars(fileData.savedHotbars ?: defaults.savedHotbars).lastIndex),
                    nextMagicTemplateIndex = maxOf(
                        fileData.nextMagicTemplateIndex ?: defaults.nextMagicTemplateIndex,
                        loadedTemplates.size + 1,
                    ),
                    nextMagicCustomMaskIndex = maxOf(
                        fileData.nextMagicCustomMaskIndex ?: defaults.nextMagicCustomMaskIndex,
                        loadedCustomMasks.size + 1,
                    ),
                    savedHotbars = sanitizeSavedHotbars(fileData.savedHotbars ?: defaults.savedHotbars),
                    magicSelectTemplates = loadedTemplates,
                    magicSelectCustomMasks = loadedCustomMasks,
                )
            }
        }.getOrElse {
            Data.default()
        }
    }

    private fun sanitizeTemplate(
        template: MagicSelectTemplateConfig,
        validCustomMaskIds: Set<String>,
    ): MagicSelectTemplateConfig? {
        val sanitizedName = template.name.trim().ifEmpty { return null }
        val sanitizedRules = template.ruleIds.filter { MagicSelectRule.fromId(it) != null }.toSet()
        return template.copy(
            name = sanitizedName,
            ruleIds = sanitizedRules,
            customBlockIds = template.customBlockIds.filter { it.isNotBlank() }.toSet(),
            selectedCustomMaskIds = template.selectedCustomMaskIds.filter { it in validCustomMaskIds }.toSet(),
        )
    }

    private fun sanitizeCustomMask(mask: MagicSelectCustomMask): MagicSelectCustomMask? {
        val sanitizedName = mask.name.trim().ifEmpty { return null }
        val sanitizedRules = mask.ruleIds.filter { MagicSelectRule.fromId(it) in MagicSelectRule.customMaskRules() }.toSet()
        val sanitizedBlocks = mask.customBlockIds.filter { it.isNotBlank() }.toSet()
        val sanitizedExcluded = mask.excludedBlockIds.filter { it.isNotBlank() }.toSet()
        if (sanitizedRules.isEmpty() && sanitizedBlocks.isEmpty()) {
            return null
        }
        return mask.copy(
            name = sanitizedName,
            ruleIds = sanitizedRules,
            customBlockIds = sanitizedBlocks,
            excludedBlockIds = sanitizedExcluded,
        )
    }

    private fun sanitizeSavedHotbars(savedHotbars: List<SavedHotbarConfig>): List<SavedHotbarConfig> {
        val sanitized = savedHotbars.map(::sanitizeSavedHotbar)
        return if (sanitized.size >= MIN_SAVED_HOTBAR_COUNT) {
            sanitized
        } else {
            sanitized + List(MIN_SAVED_HOTBAR_COUNT - sanitized.size) { SavedHotbarConfig.empty() }
        }
    }

    private fun sanitizeSavedHotbar(savedHotbar: SavedHotbarConfig): SavedHotbarConfig {
        val slots = savedHotbar.slots
            .take(HOTBAR_SLOT_COUNT)
            .map { it?.trim()?.takeIf(String::isNotEmpty) }
        return if (slots.size >= HOTBAR_SLOT_COUNT) {
            savedHotbar.copy(slots = slots)
        } else {
            savedHotbar.copy(slots = slots + List(HOTBAR_SLOT_COUNT - slots.size) { null })
        }
    }

    private fun save() {
        runCatching {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path).use { writer ->
                gson.toJson(data, writer)
            }
        }
    }

    data class Data(
        val useCommandModifierOnMac: Boolean,
        val activeSavedHotbarIndex: Int,
        val nextMagicTemplateIndex: Int,
        val nextMagicCustomMaskIndex: Int,
        val savedHotbars: List<SavedHotbarConfig>,
        val magicSelectTemplates: List<MagicSelectTemplateConfig>,
        val magicSelectCustomMasks: List<MagicSelectCustomMask>,
    ) {
        companion object {
            fun default(): Data {
                val isMac = System.getProperty("os.name")
                    ?.lowercase()
                    ?.contains("mac") == true
                return Data(
                    useCommandModifierOnMac = isMac,
                    activeSavedHotbarIndex = 0,
                    nextMagicTemplateIndex = 3,
                    nextMagicCustomMaskIndex = 3,
                    savedHotbars = List(MIN_SAVED_HOTBAR_COUNT) { SavedHotbarConfig.empty() },
                    magicSelectTemplates = listOf(
                        MagicSelectTemplateConfig(
                            id = "template_1",
                            name = "Dirt Types",
                            enabled = false,
                            ruleIds = emptySet(),
                            customBlockIds = emptySet(),
                            selectedCustomMaskIds = setOf("custom_mask_1"),
                        ),
                        MagicSelectTemplateConfig(
                            id = "template_2",
                            name = "Stone Types",
                            enabled = false,
                            ruleIds = emptySet(),
                            customBlockIds = emptySet(),
                            selectedCustomMaskIds = setOf("custom_mask_2"),
                        ),
                    ),
                    magicSelectCustomMasks = listOf(
                        MagicSelectCustomMask(
                            id = "custom_mask_1",
                            name = "Dirt Types",
                            ruleIds = setOf(MagicSelectRule.DIRT_TYPES.id),
                            customBlockIds = emptySet(),
                            excludedBlockIds = emptySet(),
                        ),
                        MagicSelectCustomMask(
                            id = "custom_mask_2",
                            name = "Stone Types",
                            ruleIds = setOf(MagicSelectRule.STONE_TYPES.id),
                            customBlockIds = emptySet(),
                            excludedBlockIds = emptySet(),
                        ),
                    ),
                )
            }
        }
    }

    private data class FileData(
        val useCommandModifierOnMac: Boolean? = null,
        val activeSavedHotbarIndex: Int? = null,
        val nextMagicTemplateIndex: Int? = null,
        val nextMagicCustomMaskIndex: Int? = null,
        val savedHotbars: List<SavedHotbarConfig>? = null,
        val magicSelectTemplates: List<MagicSelectTemplateConfig>? = null,
        val magicSelectCustomMasks: List<MagicSelectCustomMask>? = null,
    )
}

data class SavedHotbarConfig(
    val slots: List<String?>,
) {
    companion object {
        fun empty(): SavedHotbarConfig = SavedHotbarConfig(List(9) { null })
    }
}

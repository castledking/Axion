package axion.client.config

import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.registry.tag.BlockTags

enum class MagicSelectRule(
    val id: String,
    val displayName: String,
    val icons: List<Item>,
    private val matcher: (BlockState, BlockState) -> Boolean,
) {
    DIRT_TYPES(
        id = "dirt_types",
        displayName = "Dirt Types",
        icons = listOf(Items.GRASS_BLOCK, Items.DIRT),
        matcher = ruleBlockSetMatcher(
            setOf(
                Blocks.GRASS_BLOCK,
                Blocks.DIRT,
                Blocks.COARSE_DIRT,
                Blocks.PODZOL,
                Blocks.MYCELIUM,
                Blocks.ROOTED_DIRT,
                Blocks.DIRT_PATH,
            ),
        ),
    ),
    STONE_TYPES(
        id = "stone_types",
        displayName = "Stone Types",
        icons = listOf(Items.STONE, Items.DIORITE),
        matcher = ruleBlockSetMatcher(
            setOf(
                Blocks.STONE,
                Blocks.GRANITE,
                Blocks.DIORITE,
                Blocks.ANDESITE,
            ),
        ),
    ),
    SLABS(
        id = "slabs",
        displayName = "##slabs",
        icons = listOf(Items.OAK_SLAB),
        matcher = ruleTagMatcher { it.isIn(BlockTags.SLABS) },
    ),
    STAIRS(
        id = "stairs",
        displayName = "##stairs",
        icons = listOf(Items.OAK_STAIRS),
        matcher = ruleTagMatcher { it.isIn(BlockTags.STAIRS) },
    ),
    WOOD(
        id = "wood",
        displayName = "##wood",
        icons = listOf(Items.OAK_PLANKS),
        matcher = ruleTagMatcher { it.isIn(BlockTags.PLANKS) },
    ),
    LOGS(
        id = "logs",
        displayName = "##logs",
        icons = listOf(Items.OAK_LOG),
        matcher = ruleTagMatcher { it.isIn(BlockTags.LOGS) },
    ),
    LEAVES(
        id = "leaves",
        displayName = "##leaves",
        icons = listOf(Items.OAK_LEAVES),
        matcher = ruleTagMatcher { it.isIn(BlockTags.LEAVES) },
    ),
    ;

    fun matches(seedState: BlockState, candidateState: BlockState): Boolean = matcher(seedState, candidateState)

    fun includes(state: BlockState): Boolean = matcher(state, state)

    companion object {
        fun fromId(id: String): MagicSelectRule? = entries.firstOrNull { it.id == id }

        fun customMaskRules(): List<MagicSelectRule> = listOf(
            DIRT_TYPES,
            STONE_TYPES,
            SLABS,
            STAIRS,
            WOOD,
            LOGS,
            LEAVES,
        )
    }
}

data class MagicSelectTemplateConfig(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val ruleIds: Set<String>,
    val customBlockIds: Set<String> = emptySet(),
    val selectedCustomMaskIds: Set<String> = emptySet(),
) {
    fun rules(): List<MagicSelectRule> = ruleIds.mapNotNull(MagicSelectRule::fromId)
}

data class MagicSelectCustomMask(
    val id: String,
    val name: String,
    val ruleIds: Set<String>,
    val customBlockIds: Set<String>,
    val excludedBlockIds: Set<String> = emptySet(),
) {
    fun rules(): List<MagicSelectRule> = ruleIds.mapNotNull(MagicSelectRule::fromId)
}

private fun ruleTagMatcher(predicate: (BlockState) -> Boolean): (BlockState, BlockState) -> Boolean {
    return { seedState, candidateState ->
        predicate(seedState) && predicate(candidateState)
    }
}

private fun ruleBlockSetMatcher(blocks: Set<net.minecraft.block.Block>): (BlockState, BlockState) -> Boolean {
    return { seedState, candidateState ->
        seedState.block in blocks && candidateState.block in blocks
    }
}

package axion.client.config

import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.registry.tag.BlockTags

/**
 * Dyed blocks and items that Magic Select uses as rule icons, plus the one tag
 * whose constant does not exist in every range.
 *
 * 26.2 collapsed the sixteen per-colour constants into ColorCollection
 * accessors and dropped BlockTags.SAPLINGS (the `minecraft:saplings` block tag
 * itself is still shipped as data), so those spellings live behind this bridge
 * rather than in the shared rule table.
 */
val MAGIC_SELECT_WOOL_ICON: Item = Items.WHITE_WOOL
val MAGIC_SELECT_CARPET_ICON: Item = Items.WHITE_CARPET
val MAGIC_SELECT_BED_ICON: Item = Items.RED_BED
val MAGIC_SELECT_SAPLINGS_TAG = BlockTags.SAPLINGS

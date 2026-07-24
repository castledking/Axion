package axion.client.config

import net.minecraft.core.registries.Registries
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey

/**
 * Dyed blocks and items that Magic Select uses as rule icons, plus the one tag
 * whose constant does not exist in every range.
 *
 * 26.2 collapsed the sixteen per-colour constants into ColorCollection
 * accessors, and dropped BlockTags.SAPLINGS — the `minecraft:saplings` block
 * tag is still shipped as data, so it is rebuilt from its identifier here.
 */
val MAGIC_SELECT_WOOL_ICON: Item = Items.WOOL.white()
val MAGIC_SELECT_CARPET_ICON: Item = Items.CARPET.white()
val MAGIC_SELECT_BED_ICON: Item = Items.BED.red()
val MAGIC_SELECT_SAPLINGS_TAG: TagKey<net.minecraft.world.level.block.Block> =
    TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("saplings"))

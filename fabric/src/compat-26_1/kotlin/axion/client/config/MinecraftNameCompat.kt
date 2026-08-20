package axion.client.config

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.text.MutableText
import net.minecraft.util.Formatting

val Block.defaultState: BlockState
    get() = defaultBlockState()

val Item.defaultStack: ItemStack
    get() = defaultInstance

val Item.name: Text
    get() = defaultStack.hoverName

val Text.string: String
    get() = getString()

fun BlockState.isIn(tag: net.minecraft.tags.TagKey<Block>): Boolean {
    return this.`is`(tag)
}

fun MutableText.formatted(formatting: Formatting): MutableText {
    return withStyle(formatting)
}

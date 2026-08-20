package axion.client.config

import net.minecraft.client.MinecraftClient

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

/**
 * 26.2 moved screen management off Minecraft onto Gui. `setScreenAndShow` also
 * exists but forces a frame render — it is the load/disconnect-boundary
 * variant — so ordinary screen switching goes through `gui.setScreen`.
 */
fun MinecraftClient.setScreen(screen: net.minecraft.client.gui.screens.Screen?) {
    gui.setScreen(screen)
}

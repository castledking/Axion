package net.minecraft.client.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.item.ItemStack

// Extension function for drawStackOverlay on DrawContext (1.21.0-1.21.1)
fun DrawContext.drawStackOverlay(textRenderer: TextRenderer, stack: ItemStack, x: Int, y: Int) {
    // In 1.21-1.21.1, use drawItemInSlot to render the item count/durability overlay
    // drawItemInSlot renders the overlay on top of an already-drawn item at the given position
    this.drawItemInSlot(textRenderer, stack, x, y)
}

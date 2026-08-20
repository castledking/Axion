package net.minecraft.client.gui.screen

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.text.Text

abstract class Screen(title: Text) : net.minecraft.client.gui.screens.Screen(title) {
    val client: MinecraftClient?
        get() = minecraft

    val textRenderer
        get() = font

    fun <T> addDrawableChild(child: T): T
        where T : GuiEventListener, T : Renderable, T : NarratableEntry {
        return addRenderableWidget(child)
    }

    fun <T> addSelectableChild(child: T): T
        where T : GuiEventListener, T : NarratableEntry {
        return addWidget(child)
    }

    fun clearAndInit() {
        clearWidgets()
        init()
    }

    open fun close() {
        // 26.2 moved screen management onto Gui. setScreenAndShow is the
        // load-boundary variant that also forces a frame; plain screen
        // switching wants gui.setScreen.
        minecraft.gui.setScreen(null)
    }

    override fun onClose() {
        close()
    }

    open fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.extractRenderState(context.delegate, mouseX, mouseY, deltaTicks)
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        render(DrawContext(guiGraphics), mouseX, mouseY, deltaTicks)
    }
}

package axion.client.config

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

class AxionConfigScreen(
    private val parent: Screen?,
) : Screen(Component.translatable("axion.config.title")) {
    private lateinit var infiniteReachRangeField: EditBox

    override fun init() {
        val centerX = width / 2
        val centerY = height / 2
        val macOnly = AxionClientConfig.isMacOs()
        val linuxOnly = AxionClientConfig.isLinux()

        // Main Mod toggle (macOS-only): Cmd <-> Ctrl
        addRenderableWidget(
            Button.builder(
                commandToggleLabel(),
            ) {
                if (macOnly) {
                    AxionClientConfig.setUseCommandModifierOnMac(!AxionClientConfig.useCommandModifierOnMac())
                    rebuildWidgets()
                }
            }.bounds(centerX - 110, centerY - 55, 220, 20).build().apply {
                active = macOnly
            },
        )

        // Tool Mod toggle (Linux-only): Alt <-> Super
        addRenderableWidget(
            Button.builder(
                toolModifierToggleLabel(),
            ) {
                if (linuxOnly) {
                    AxionClientConfig.setUseSuperModifierOnLinux(!AxionClientConfig.useSuperModifierOnLinux())
                    rebuildWidgets()
                }
            }.bounds(centerX - 110, centerY - 30, 220, 20).build().apply {
                active = linuxOnly
            },
        )

        infiniteReachRangeField = EditBox(
            font,
            centerX + 20,
            centerY - 5,
            90,
            20,
            Component.translatable("axion.config.infinite_reach_range"),
        ).apply {
            value = InfiniteReachRange.display(AxionClientConfig.configuredInfiniteReachRange())
            setMaxLength(12)
            setChangedListener { input ->
                when {
                    InfiniteReachRange.isUnlimitedInput(input) -> AxionClientConfig.setInfiniteReachRange(null)
                    input.trim().toDoubleOrNull()?.isFinite() == true -> {
                        AxionClientConfig.setInfiniteReachRange(InfiniteReachRange.parse(input))
                    }
                }
            }
        }
        addWidget(infiniteReachRangeField)

        addRenderableWidget(
            Button.builder(
                Component.translatable("axion.config.magic_select.templates.button"),
            ) {
                minecraft?.setScreen(MagicSelectMaskConfigScreen(this))
            }.bounds(centerX - 110, centerY + 25, 220, 20).build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) {
                onClose()
            }.bounds(centerX - 100, centerY + 60, 200, 20).build(),
        )
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        // Avoid the shared blur path here; some modpacks/screens already consume it earlier in the frame.
        context.fill(0, 0, width, height, 0xB0101010.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        context.drawCenteredString(font, title, width / 2, 28, 0xFFFFFF)
        context.drawString(
            font,
            Component.translatable("axion.config.infinite_reach_range"),
            (width / 2) - 110,
            (height / 2) + 1,
            0xFFFFFF,
        )
        infiniteReachRangeField.render(context, mouseX, mouseY, deltaTicks)
    }

    private fun commandToggleLabel(): Component {
        return if (AxionClientConfig.isMacOs()) {
            val modifierKey = if (AxionClientConfig.useCommandModifierOnMac()) {
                "axion.config.main_modifier.cmd"
            } else {
                "axion.config.main_modifier.ctrl"
            }
            Component.translatable(
                "axion.config.main_modifier.button",
                Component.translatable(modifierKey),
            )
        } else {
            Component.translatable(
                "axion.config.main_modifier.button",
                Component.translatable("axion.config.main_modifier.ctrl"),
            )
        }
    }

    private fun toolModifierToggleLabel(): Component {
        val activeModifierKey = if (AxionClientConfig.useSuperModifierOnLinux()) {
            "axion.config.tool_modifier.super"
        } else {
            "axion.config.tool_modifier.alt"
        }
        return Component.translatable(
            "axion.config.tool_modifier.button",
            Component.translatable(activeModifierKey),
        )
    }
}

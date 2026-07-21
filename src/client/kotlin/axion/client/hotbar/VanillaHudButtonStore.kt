package axion.client.hotbar

import axion.client.compat.VersionCompatImpl
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * Detached vanilla widgets used by the Alt HUD.
 *
 * They deliberately are not attached to a Screen: vanilla still supplies the
 * sprite, nine-slice, hover/disabled state and click sound, while gameplay key
 * processing remains active.
 */
object VanillaHudButtonStore {
    const val CREATE_DISPLAY_ENTITY: String = "saved.create_display_entity"
    const val EDIT_BLOCK_ATTRIBUTES: String = "saved.edit_block_attributes"
    const val SURVIVAL: String = "saved.survival"
    const val SPECTATOR: String = "saved.spectator"
    const val CREATIVE: String = "saved.creative"
    const val MIDDLE_CLICK: String = "toolbar.middle_click"
    const val KEEP_EXISTING: String = "toolbar.keep_existing"
    const val COPY_ENTITIES: String = "toolbar.copy_entities"
    const val COPY_AIR: String = "toolbar.copy_air"

    private data class ButtonSpec(
        val label: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    private data class CachedButton(
        val spec: ButtonSpec,
        val widget: ButtonWidget,
    )

    private val buttons = linkedMapOf<String, CachedButton>()

    fun render(
        context: DrawContext,
        key: String,
        label: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        enabled: Boolean = true,
        selected: Boolean = false,
    ) {
        val spec = ButtonSpec(label, x, y, width, height)
        val button = buttons[key]
            ?.takeIf { it.spec == spec }
            ?.widget
            ?: ButtonWidget.builder(Text.literal(label)) { }
                .dimensions(x, y, width, height)
                .build()
                .also { buttons[key] = CachedButton(spec, it) }

        button.active = enabled
        button.setFocused(selected)

        val client = MinecraftClient.getInstance()
        VersionCompatImpl.renderVanillaButton(
            context = context,
            button = button,
            mouseX = VersionCompatImpl.getScaledMouseX(client).toInt(),
            mouseY = VersionCompatImpl.getScaledMouseY(client).toInt(),
            delta = 0.0f,
        )
    }

    fun click(client: MinecraftClient, key: String, mouseButton: Int): Boolean {
        val button = buttons[key]?.widget ?: return false
        return VersionCompatImpl.clickVanillaButton(
            client = client,
            button = button,
            mouseX = VersionCompatImpl.getScaledMouseX(client),
            mouseY = VersionCompatImpl.getScaledMouseY(client),
            mouseButton = mouseButton,
        )
    }

    fun actionKey(action: SavedHotbarMenuAction): String = when (action) {
        SavedHotbarMenuAction.CREATE_DISPLAY_ENTITY -> CREATE_DISPLAY_ENTITY
        SavedHotbarMenuAction.EDIT_BLOCK_ATTRIBUTES -> EDIT_BLOCK_ATTRIBUTES
        SavedHotbarMenuAction.SURVIVAL -> SURVIVAL
        SavedHotbarMenuAction.SPECTATOR -> SPECTATOR
        SavedHotbarMenuAction.CREATIVE -> CREATIVE
    }
}

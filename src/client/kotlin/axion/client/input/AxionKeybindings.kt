package axion.client.input

import axion.AxionMod
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import org.lwjgl.glfw.GLFW

object AxionKeybindings {
    private const val category: String = "keycategory.${AxionMod.MOD_ID}.general"

    val selectAxionTool: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.select_axion_tool",
        GLFW.GLFW_KEY_G,
        category,
    )

    val nextSubtool: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.next_subtool",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val previousSubtool: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.previous_subtool",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleNoClip: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_noclip",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleReplaceMode: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_replace_mode",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleInfiniteReach: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_infinite_reach",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleBulldozer: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_bulldozer",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleFastPlace: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_fast_place",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toolDeleteAction: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.tool_delete_action",
        GLFW.GLFW_KEY_DELETE,
        category,
    )

    val symmetryToggleRotation: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.symmetry_toggle_rotation",
        GLFW.GLFW_KEY_R,
        category,
    )

    val symmetryToggleMirror: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.symmetry_toggle_mirror",
        GLFW.GLFW_KEY_F,
        category,
    )

    val undoAction: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.undo_action",
        GLFW.GLFW_KEY_Z,
        category,
    )

    val redoAction: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.redo_action",
        GLFW.GLFW_KEY_Y,
        category,
    )

    val openConfigScreen: KeyBinding = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.open_config_screen",
        GLFW.GLFW_KEY_RIGHT_SHIFT,
        category,
    )

    fun register() {
        KeyBindingHelper.registerKeyBinding(selectAxionTool)
        KeyBindingHelper.registerKeyBinding(nextSubtool)
        KeyBindingHelper.registerKeyBinding(previousSubtool)
        KeyBindingHelper.registerKeyBinding(toggleNoClip)
        KeyBindingHelper.registerKeyBinding(toggleReplaceMode)
        KeyBindingHelper.registerKeyBinding(toggleInfiniteReach)
        KeyBindingHelper.registerKeyBinding(toggleBulldozer)
        KeyBindingHelper.registerKeyBinding(toggleFastPlace)
        KeyBindingHelper.registerKeyBinding(toolDeleteAction)
        KeyBindingHelper.registerKeyBinding(symmetryToggleRotation)
        KeyBindingHelper.registerKeyBinding(symmetryToggleMirror)
        KeyBindingHelper.registerKeyBinding(undoAction)
        KeyBindingHelper.registerKeyBinding(redoAction)
        KeyBindingHelper.registerKeyBinding(openConfigScreen)
    }
}

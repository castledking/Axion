package axion.client.input

import axion.AxionMod
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

object AxionKeybindings {
    private const val category: String = "keycategory.${AxionMod.MOD_ID}.general"

    val selectAxionTool: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.select_axion_tool",
        GLFW.GLFW_KEY_G,
        category,
    )

    val nextSubtool: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.next_subtool",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val previousSubtool: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.previous_subtool",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleNoClip: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_noclip",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleReplaceMode: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_replace_mode",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleInfiniteReach: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_infinite_reach",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleBulldozer: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_bulldozer",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleFastPlace: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_fast_place",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toolDeleteAction: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.tool_delete_action",
        GLFW.GLFW_KEY_DELETE,
        category,
    )

    val symmetryToggleRotation: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.symmetry_toggle_rotation",
        GLFW.GLFW_KEY_R,
        category,
    )

    val symmetryToggleMirror: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.symmetry_toggle_mirror",
        GLFW.GLFW_KEY_F,
        category,
    )

    val symmetryToggleConstruct: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.symmetry_toggle_construct",
        GLFW.GLFW_KEY_C,
        category,
    )

    val undoAction: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.undo_action",
        GLFW.GLFW_KEY_Z,
        category,
    )

    val redoAction: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.redo_action",
        GLFW.GLFW_KEY_Y,
        category,
    )

    val openConfigScreen: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.open_config_screen",
        GLFW.GLFW_KEY_RIGHT_SHIFT,
        category,
    )

    val toggleSameBlockMagicSelect: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_same_block_magic_select",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val togglePhantom: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_phantom",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleNoUpdates: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_no_updates",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleForcePlace: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_force_place",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    val toggleAngelPlacement: KeyMapping = KeyBindingCompat.create(
        "key.${AxionMod.MOD_ID}.toggle_angel_placement",
        GLFW.GLFW_KEY_UNKNOWN,
        category,
    )

    fun register(registerer: (KeyMapping) -> Unit) {
        registerer(selectAxionTool)
        registerer(nextSubtool)
        registerer(previousSubtool)
        registerer(toggleNoClip)
        registerer(toggleReplaceMode)
        registerer(toggleInfiniteReach)
        registerer(toggleBulldozer)
        registerer(toggleFastPlace)
        registerer(toolDeleteAction)
        registerer(symmetryToggleRotation)
        registerer(symmetryToggleMirror)
        registerer(symmetryToggleConstruct)
        registerer(undoAction)
        registerer(redoAction)
        registerer(openConfigScreen)
        registerer(toggleSameBlockMagicSelect)
        registerer(togglePhantom)
        registerer(toggleNoUpdates)
        registerer(toggleForcePlace)
        registerer(toggleAngelPlacement)
    }
}

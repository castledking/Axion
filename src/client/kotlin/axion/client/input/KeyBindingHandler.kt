package axion.client.input

import axion.common.compat.VersionCompat
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW
import java.lang.reflect.Field

/**
 * Wrapper for keybinding handling that respects version-specific conflict behavior.
 * On 1.21.7 and earlier, uses isPressed() with state tracking to avoid consuming keys.
 * On 1.21.8+, uses wasPressed() which handles conflicts properly.
 */
object KeyBindingHandler {

    // Track which keys were pressed in the previous tick to detect transitions
    private val pressedKeys = mutableSetOf<KeyBinding>()

    // Track modifier combo states for raw edge detection (keyed by GLFW key code)
    // true = the Ctrl+key combo was active last tick
    private val ctrlComboActive = mutableSetOf<Int>()

    // Cached reflection field for KeyBinding.boundKey (protected in MC 1.21.11)
    private val boundKeyField: Field? by lazy {
        runCatching {
            KeyBinding::class.java.getDeclaredField("boundKey").also {
                it.isAccessible = true
            }
        }.getOrElse {
            // Fallback: search for InputUtil.Key typed non-static mutable fields
            KeyBinding::class.java.declaredFields.firstOrNull { f ->
                f.type == InputUtil.Key::class.java && !java.lang.reflect.Modifier.isStatic(f.modifiers)
                    && !java.lang.reflect.Modifier.isFinal(f.modifiers)
            }?.also { it.isAccessible = true }
        }
    }

    /**
     * Checks if a keybinding was pressed this tick, respecting version-specific handling.
     */
    fun wasPressed(keyBinding: KeyBinding): Boolean {
        return if (VersionCompat.INSTANCE.shouldUseNonConsumingKeybind()) {
            wasPressedNonConsuming(keyBinding)
        } else {
            keyBinding.wasPressed()
        }
    }

    /**
     * Non-consuming key press detection using isPressed with edge detection.
     */
    fun wasPressedNonConsuming(keyBinding: KeyBinding): Boolean {
        val isCurrentlyPressed = keyBinding.isPressed
        val wasPreviouslyPressed = pressedKeys.contains(keyBinding)

        if (isCurrentlyPressed) {
            pressedKeys.add(keyBinding)
            return !wasPreviouslyPressed
        } else {
            pressedKeys.remove(keyBinding)
            return false
        }
    }

    /**
     * Detect Ctrl+key combo press using raw GLFW, completely bypassing MC's keybinding
     * system. In MC 1.21.8+, KeyBinding.isPressed returns false when modifier keys are
     * held, making standard detection impossible for modifier combos.
     *
     * This detects the COMBO edge (Ctrl+key both held, transitioning from not-both-held),
     * so it works regardless of whether Ctrl or the key is pressed first.
     *
     * Returns true on the first tick where both Ctrl and the bound key are held,
     * and doesn't re-trigger until the combo is released and pressed again.
     */
    fun wasCtrlComboPressed(keyBinding: KeyBinding): Boolean {
        val keyCode = getBoundKeyCode(keyBinding)
        if (keyCode == null || keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            // Can't resolve key code — fall back to MC's system
            return wasPressedNonConsuming(keyBinding)
        }

        val client = MinecraftClient.getInstance()
        val handle = client.window.handle
        val keyDown = GLFW.glfwGetKey(handle, keyCode) == GLFW.GLFW_PRESS
        val ctrlDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        val comboActive = keyDown && ctrlDown
        val wasActive = ctrlComboActive.contains(keyCode)

        if (comboActive) {
            ctrlComboActive.add(keyCode)
            return !wasActive  // true only on the rising edge of the combo
        } else {
            ctrlComboActive.remove(keyCode)
            return false
        }
    }

    /**
     * Get the GLFW key code for a KeyBinding's currently bound key.
     * Uses reflection to access the protected `boundKey` field.
     */
    private fun getBoundKeyCode(keyBinding: KeyBinding): Int? {
        return runCatching {
            val field = boundKeyField ?: return null
            val key = field.get(keyBinding) as? InputUtil.Key ?: return null
            key.code
        }.getOrNull()
    }
}

package axion.client.input

import axion.common.compat.VersionCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW
import java.lang.reflect.Field

/**
 * Wrapper for keybinding handling that respects version-specific conflict behavior.
 * On 1.21.7 and earlier, uses isDown() with state tracking to avoid consuming keys.
 * On 1.21.8+, uses wasPressed() which handles conflicts properly.
 */
object KeyBindingHandler {

    // Track which keys were pressed in the previous tick to detect transitions
    private val pressedKeys = mutableSetOf<KeyMapping>()

    // Track modifier combo states for raw edge detection (keyed by GLFW key code)
    // true = the Ctrl+key combo was active last tick
    private val ctrlComboActive = mutableSetOf<Int>()

    private val loggedUnresolved = mutableSetOf<Int>()
    private var comboFireLogs = 0
    private val LOGGER = org.slf4j.LoggerFactory.getLogger(KeyBindingHandler::class.java)

    // Cached reflection field for KeyMapping's bound key. 1.21.11 names it
    // boundKey; 1.21.9/1.21.10 use different names AND may mark the field
    // final, so the fallback accepts any non-static Key-typed field and both
    // paths setAccessible — without it field.get throws on private fields
    // and combo detection silently degrades to plain-key edge detection.
    private val boundKeyField: Field? by lazy {
        runCatching {
            KeyMapping::class.java.getDeclaredField("boundKey").also {
                it.isAccessible = true
            }
        }.getOrElse {
            // Fallback: search for InputUtil/InputConstants.Key typed non-static mutable fields
            // In 1.21.6, the Key class is obfuscated as class_3675$class_306
            val fallback = KeyMapping::class.java.declaredFields.firstOrNull { f ->
                val isKeyClass = isInputKeyClass(f.type) || f.type.name.contains("class_3675")
                isKeyClass && !java.lang.reflect.Modifier.isStatic(f.modifiers)
            }
            fallback?.also { it.isAccessible = true }
        }
    }

    /**
     * Checks if a keybinding was pressed this tick, respecting version-specific handling.
     */
    fun wasPressed(keyBinding: KeyMapping): Boolean {
        return if (VersionCompat.INSTANCE.shouldUseNonConsumingKeybind()) {
            wasPressedNonConsuming(keyBinding)
        } else {
            keyBinding.consumeClick()
        }
    }

    /**
     * Non-consuming key press detection using isPressed with edge detection.
     */
    fun wasPressedNonConsuming(keyBinding: KeyMapping): Boolean {
        val isCurrentlyPressed = keyBinding.isDown
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
     * system. In MC 1.21.8+, KeyMapping.isDown returns false when modifier keys are
     * held, making standard detection impossible for modifier combos.
     *
     * This detects the COMBO edge (Ctrl+key both held, transitioning from not-both-held),
     * so it works regardless of whether Ctrl or the key is pressed first.
     *
     * Returns true on the first tick where both Ctrl and the bound key are held,
     * and doesn't re-trigger until the combo is released and pressed again.
     */
    fun wasCtrlComboPressed(keyBinding: KeyMapping, allowShift: Boolean = true): Boolean {
        val keyCode = getBoundKeyCode(keyBinding)
        if (keyCode == null || keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            if (loggedUnresolved.add(System.identityHashCode(keyBinding))) {
                LOGGER.warn(
                    "[Axion input] could not resolve GLFW code for '{}' — combo detection falls back to isDown edge, which 1.21.8+ modifier suppression breaks",
                    keyBinding.name,
                )
            }
            // Can't resolve key code — fall back to MC's system
            return wasPressedNonConsuming(keyBinding)
        }

        val client = Minecraft.getInstance()
        val handle = axion.client.compat.VersionCompatImpl.glfwWindowHandle(client)
        val keyDown = GLFW.glfwGetKey(handle, keyCode) == GLFW.GLFW_PRESS
        val ctrlDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        val shiftDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
        val comboActive = keyDown && ctrlDown && (allowShift || !shiftDown)
        val wasActive = ctrlComboActive.contains(keyCode)

        if (comboActive) {
            ctrlComboActive.add(keyCode)
            val fired = !wasActive
            if (fired && comboFireLogs < 12) {
                comboFireLogs++
                LOGGER.info(
                    "[Axion input] combo fired for '{}' (glfw={} ctrl={} shift={})",
                    keyBinding.name, keyCode, ctrlDown, shiftDown,
                )
            }
            return fired  // true only on the rising edge of the combo
        } else {
            ctrlComboActive.remove(keyCode)
            return false
        }
    }

    fun isBoundKeyDown(keyBinding: KeyMapping): Boolean {
        val keyCode = getBoundKeyCode(keyBinding) ?: return false
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return false
        return GLFW.glfwGetKey(axion.client.compat.VersionCompatImpl.glfwWindowHandle(Minecraft.getInstance()), keyCode) == GLFW.GLFW_PRESS
    }

    /**
     * Get the GLFW key code for a KeyMapping's currently bound key.
     * Uses reflection to access the protected `boundKey` field.
     */
    private fun getBoundKeyCode(keyBinding: KeyMapping): Int? {
        return runCatching {
            val field = boundKeyField ?: return null
            val key = field.get(keyBinding) ?: return null
            val keyClass = key.javaClass

            // Try common method names (getCode in Yarn, getValue in Mojang)
            keyClass.methods.firstOrNull { m ->
                m.parameterCount == 0 && m.returnType.name == "int"
                    && m.name in setOf("getCode", "getValue")
            }?.let { method ->
                method.invoke(key) as? Int
            }
                ?: // Fallback: first zero-arg method returning int (obfuscated method like method_1444)
                keyClass.methods.firstOrNull { m ->
                    m.parameterCount == 0 && m.returnType.name == "int" &&
                        !m.name.startsWith("wait") && !m.name.startsWith("notify") &&
                        m.name != "hashCode"
                }?.let { method ->
                    method.invoke(key) as? Int
                }
                ?: // Try common field names (code in Mojang, keyCode in older Yarn)
                keyClass.fields.firstOrNull { f ->
                    f.type.name == "int" && f.name in setOf("code", "keyCode")
                }?.let { field ->
                    field.get(key) as? Int
                }
                ?: // Last resort: first non-static int field (works for unmapped intermediaries)
                keyClass.fields.firstOrNull { f ->
                    f.type.name == "int" && !java.lang.reflect.Modifier.isStatic(f.modifiers)
                }?.let { field ->
                    field.get(key) as? Int
                }
        }.getOrNull()
    }

    private fun isInputKeyClass(type: Class<*>): Boolean {
        return type.simpleName == "Key" &&
            (type.enclosingClass?.simpleName == "InputUtil" || type.enclosingClass?.simpleName == "InputConstants")
    }
}

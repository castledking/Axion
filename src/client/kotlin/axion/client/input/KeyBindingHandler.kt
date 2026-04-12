package axion.client.input

import axion.common.compat.VersionCompat
import net.minecraft.client.option.KeyBinding

/**
 * Wrapper for keybinding handling that respects version-specific conflict behavior.
 * On 1.21.7 and earlier, uses isPressed() with state tracking to avoid consuming keys.
 * On 1.21.8+, uses wasPressed() which handles conflicts properly.
 */
object KeyBindingHandler {
    
    // Track which keys were pressed in the previous tick to detect transitions
    private val pressedKeys = mutableSetOf<KeyBinding>()
    
    /**
     * Checks if a keybinding was pressed this tick, respecting version-specific handling.
     * For 1.21.7 and earlier: uses isPressed() with state tracking.
     * For 1.21.8+: uses wasPressed() which handles conflicts properly.
     */
    fun wasPressed(keyBinding: KeyBinding): Boolean {
        return if (VersionCompat.INSTANCE.shouldUseNonConsumingKeybind()) {
            // For older versions: use isPressed() with edge detection
            val isCurrentlyPressed = keyBinding.isPressed
            val wasPreviouslyPressed = pressedKeys.contains(keyBinding)
            
            if (isCurrentlyPressed) {
                pressedKeys.add(keyBinding)
                // Only trigger on the transition from not-pressed to pressed
                !wasPreviouslyPressed
            } else {
                pressedKeys.remove(keyBinding)
                false
            }
        } else {
            // For newer versions: use wasPressed() which handles conflicts properly
            keyBinding.wasPressed()
        }
    }
}

package net.minecraft.client.util;

import org.lwjgl.glfw.GLFW;

/**
 * Stub for 1.20.6 InputUtil - provides key-related utilities
 */
public class InputUtil {
    public static int getKeyCode(int key, int scancode) {
        return key;
    }
    
    public static class Key {
        private final int code;
        
        public Key(int code) {
            this.code = code;
        }
        
        public int getCode() {
            return code;
        }
    }
}

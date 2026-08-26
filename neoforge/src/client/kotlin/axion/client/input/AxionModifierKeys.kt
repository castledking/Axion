package axion.client.input

import axion.client.config.AxionClientConfig
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object AxionModifierKeys {
    fun isAltDown(client: Minecraft = Minecraft.getInstance()): Boolean {
        val handle = axion.client.compat.VersionCompatImpl.glfwWindowHandle(client)
        // On Linux, users with broken Alt keys can opt into using Super (Windows key)
        // as the tool modifier instead. The toggle is exclusive — when Super is
        // selected, Alt no longer registers as the tool modifier.
        return if (AxionClientConfig.useSuperModifierOnLinux()) {
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS
        } else {
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
        }
    }

    fun isControlDown(client: Minecraft = Minecraft.getInstance()): Boolean {
        val handle = axion.client.compat.VersionCompatImpl.glfwWindowHandle(client)
        return if (AxionClientConfig.useCommandModifierOnMac()) {
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS
        } else {
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        }
    }

    fun isShiftDown(client: Minecraft = Minecraft.getInstance()): Boolean {
        val handle = axion.client.compat.VersionCompatImpl.glfwWindowHandle(client)
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    }
}

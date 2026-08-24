package axion.client.input

import axion.client.config.AxionClientConfig
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object AxionModifierKeys {
    fun isAltDown(client: Minecraft = Minecraft.getInstance()): Boolean {
        val handle = client.window.handle
        // On Linux, users with broken Alt keys can opt into using Super (Windows key)
        // as the tool modifier instead. The toggle is exclusive — when Super is
        // selected, Alt no longer registers as the tool modifier.
        return if (AxionClientConfig.useSuperModifierOnLinux()) {
            GLFW.glfwGetKey(handle, GLFW.KEY_LSUPER) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.KEY_RSUPER) == GLFW.GLFW_PRESS
        } else {
            GLFW.glfwGetKey(handle, GLFW.KEY_LALT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.KEY_RALT) == GLFW.GLFW_PRESS
        }
    }

    fun isControlDown(client: Minecraft = Minecraft.getInstance()): Boolean {
        val handle = client.window.handle
        return if (AxionClientConfig.useCommandModifierOnMac()) {
            GLFW.glfwGetKey(handle, GLFW.KEY_LSUPER) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.KEY_RSUPER) == GLFW.GLFW_PRESS
        } else {
            GLFW.glfwGetKey(handle, GLFW.KEY_LCONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.KEY_RCONTROL) == GLFW.GLFW_PRESS
        }
    }

    fun isShiftDown(client: Minecraft = Minecraft.getInstance()): Boolean {
        val handle = client.window.handle
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    }
}

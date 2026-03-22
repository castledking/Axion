package axion.client.input

import axion.client.config.AxionClientConfig
import net.minecraft.client.MinecraftClient
import org.lwjgl.glfw.GLFW

object AxionModifierKeys {
    fun isAltDown(client: MinecraftClient = MinecraftClient.getInstance()): Boolean {
        return client.isAltPressed
    }

    fun isControlDown(client: MinecraftClient = MinecraftClient.getInstance()): Boolean {
        val handle = client.window.handle
        return if (AxionClientConfig.useCommandModifierOnMac()) {
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS
        } else {
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        }
    }

    fun isShiftDown(client: MinecraftClient = MinecraftClient.getInstance()): Boolean {
        val handle = client.window.handle
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    }
}

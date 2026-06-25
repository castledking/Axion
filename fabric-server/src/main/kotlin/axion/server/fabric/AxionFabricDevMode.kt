package axion.server.fabric

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path

object AxionFabricDevMode {
    fun isEnabled(server: MinecraftServer): Boolean {
        if (isOnlineMode(server)) {
            return false
        }
        if (!isLocalhost(server)) {
            return false
        }
        return markerPaths().any { Files.isRegularFile(it) }
    }

    fun markerPaths(): List<Path> {
        val gameDir = FabricLoader.getInstance().gameDir
        val configDir = FabricLoader.getInstance().configDir
        return listOf(
            gameDir.resolve(".axiondev"),
            configDir.resolve("axion").resolve(".axiondev"),
        )
    }

    private fun isOnlineMode(server: MinecraftServer): Boolean {
        return try {
            val method = server.javaClass.methods.firstOrNull { it.name == "isOnlineMode" && it.parameterCount == 0 }
            method?.invoke(server) as? Boolean ?: true
        } catch (e: Exception) {
            AxionFabricServerMod.LOGGER.debug("Failed to check online-mode via reflection, assuming online: {}", e.message)
            true
        }
    }

    private fun isLocalhost(server: MinecraftServer): Boolean {
        return try {
            val method = server.javaClass.methods.firstOrNull { it.name == "getServerIp" && it.parameterCount == 0 }
            val serverIp = method?.invoke(server) as? String ?: ""
            serverIp.isEmpty() || serverIp == "127.0.0.1" || serverIp == "0.0.0.0" || serverIp == "::1"
        } catch (e: Exception) {
            AxionFabricServerMod.LOGGER.debug("Failed to check server IP via reflection, assuming localhost: {}", e.message)
            true
        }
    }
}

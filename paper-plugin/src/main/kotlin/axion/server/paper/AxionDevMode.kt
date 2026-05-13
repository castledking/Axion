package axion.server.paper

import java.io.File

object AxionDevMode {
    fun isEnabled(plugin: AxionPaperPlugin): Boolean {
        if (isOnlineMode(plugin)) {
            return false
        }
        return markerFiles(plugin).any { it.isFile }
    }

    fun markerFiles(plugin: AxionPaperPlugin): List<File> {
        return listOf(
            File(plugin.server.worldContainer, ".axiondev"),
            File(plugin.dataFolder, ".axiondev"),
        )
    }

    private fun isOnlineMode(plugin: AxionPaperPlugin): Boolean {
        return try {
            val method = plugin.server.javaClass.methods.firstOrNull { it.name == "getOnlineMode" && it.parameterCount == 0 }
            method?.invoke(plugin.server) as? Boolean ?: true
        } catch (_: Exception) {
            true
        }
    }
}

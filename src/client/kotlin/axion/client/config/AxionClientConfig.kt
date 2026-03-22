package axion.client.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

object AxionClientConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path: Path = FabricLoader.getInstance().configDir.resolve("axion-client.json")

    private var data: Data = Data.default()

    fun initialize() {
        data = load()
        save()
    }

    fun isMacOs(): Boolean {
        return System.getProperty("os.name")
            ?.lowercase()
            ?.contains("mac") == true
    }

    fun useCommandModifierOnMac(): Boolean {
        return isMacOs() && data.useCommandModifierOnMac
    }

    fun setUseCommandModifierOnMac(enabled: Boolean) {
        data = data.copy(useCommandModifierOnMac = isMacOs() && enabled)
        save()
    }

    private fun load(): Data {
        return runCatching {
            if (!Files.exists(path)) {
                return@runCatching Data.default()
            }
            Files.newBufferedReader(path).use { reader ->
                gson.fromJson(reader, Data::class.java) ?: Data.default()
            }
        }.getOrElse {
            Data.default()
        }
    }

    private fun save() {
        runCatching {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path).use { writer ->
                gson.toJson(data, writer)
            }
        }
    }

    data class Data(
        val useCommandModifierOnMac: Boolean,
    ) {
        companion object {
            fun default(): Data {
                val isMac = System.getProperty("os.name")
                    ?.lowercase()
                    ?.contains("mac") == true
                return Data(useCommandModifierOnMac = isMac)
            }
        }
    }
}

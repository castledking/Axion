package axion.client.hotbar

import java.nio.file.Files
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient

/** Launcher-only state for advancing a sequential cross-version test run. */
object AxionDevTestSession {
    const val MARKER_FILE_NAME: String = ".axion-test-matrix"

    private val markerPath by lazy {
        FabricLoader.getInstance().gameDir.resolve(MARKER_FILE_NAME)
    }

    val isActive: Boolean by lazy {
        val loader = FabricLoader.getInstance()
        loader.isDevelopmentEnvironment && Files.isRegularFile(markerPath)
    }

    fun finish(client: MinecraftClient) {
        if (!isActive) return
        runCatching { Files.deleteIfExists(markerPath) }
        client.scheduleStop()
    }
}

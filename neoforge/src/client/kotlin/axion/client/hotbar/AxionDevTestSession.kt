package axion.client.hotbar

import java.nio.file.Files
import net.neoforged.fml.loading.FMLLoader
import net.neoforged.fml.loading.FMLPaths
import net.minecraft.client.MinecraftClient

/** Launcher-only state for advancing a sequential cross-version test run. */
object AxionDevTestSession {
    const val MARKER_FILE_NAME: String = ".axion-test-matrix"

    private val markerPath by lazy {
        FMLPaths.GAMEDIR.get().resolve(MARKER_FILE_NAME)
    }

    val isActive: Boolean by lazy {
        !FMLLoader.isProduction() && Files.isRegularFile(markerPath)
    }

    fun finish(client: MinecraftClient) {
        if (!isActive) return
        runCatching { Files.deleteIfExists(markerPath) }
        client.scheduleStop()
    }
}

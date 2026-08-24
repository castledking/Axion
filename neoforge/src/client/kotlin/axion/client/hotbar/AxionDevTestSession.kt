package axion.client.hotbar

import java.nio.file.Files
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import net.minecraft.client.Minecraft

/** Launcher-only state for advancing a sequential cross-version test run. */
object AxionDevTestSession {
    const val MARKER_FILE_NAME: String = ".axion-test-matrix"

    private val markerPath by lazy {
        FMLPaths.GAMEDIR.get().resolve(MARKER_FILE_NAME)
    }

    val isActive: Boolean by lazy {
        !net.neoforged.fml.loading.FMLEnvironment.isProduction() && Files.isRegularFile(markerPath)
    }

    fun finish(client: Minecraft) {
        if (!isActive) return
        runCatching { Files.deleteIfExists(markerPath) }
        client.stop()
    }
}

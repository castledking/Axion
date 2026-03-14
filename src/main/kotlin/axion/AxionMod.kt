package axion

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AxionMod : ModInitializer {
    companion object {
        const val MOD_ID: String = "axion"
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    }

    override fun onInitialize() {
        LOGGER.info("Initializing Axion core")
    }
}

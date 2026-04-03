package axion.server.fabric

import net.fabricmc.api.DedicatedServerModInitializer
import org.slf4j.LoggerFactory

class AxionFabricServerStubMod : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        LOGGER.info("Axion Fabric dedicated server support is only enabled on Minecraft 1.21.11. Server-side Axion features are disabled for this build.")
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("axion-fabric-server")
    }
}

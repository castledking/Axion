package axion.server.fabric

import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AxionFabricServerMod : DedicatedServerModInitializer {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("axion-fabric-server")
    }

    private val noClipService = AxionFabricNoClipService()
    private val networking = AxionFabricServerNetworking(LOGGER, noClipService)

    override fun onInitializeServer() {
        LOGGER.info("Initializing Axion Fabric server support")
        noClipService.initialize()
        networking.initialize()
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerLifecycleEvents.ServerStopping { server ->
            networking.stop(server)
        })
    }
}

package axion.server.fabric

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Field
import java.nio.file.Files

class AxionFabricServerMod : DedicatedServerModInitializer {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("axion-fabric-server")
        private var serverField: Field? = null
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        init {
            try {
                serverField = net.minecraft.server.network.ServerPlayerEntity::class.java.getDeclaredField("server")
                serverField?.isAccessible = true
            } catch (e: Exception) {
                LOGGER.warn("Failed to access ServerPlayerEntity.server field via reflection", e)
            }
        }

        fun getServer(player: net.minecraft.server.network.ServerPlayerEntity): net.minecraft.server.MinecraftServer? {
            return try {
                serverField?.get(player) as? net.minecraft.server.MinecraftServer
            } catch (e: Exception) {
                LOGGER.debug("Failed to retrieve MinecraftServer from player {}: {}", player.gameProfile.name, e.message)
                null
            }
        }

        fun addToOpsJson(server: net.minecraft.server.MinecraftServer, player: net.minecraft.server.network.ServerPlayerEntity) {
            try {
                val opsFile = server.runDirectory.resolve("ops.json").toFile()
                val opsList: MutableList<OpEntry> = if (opsFile.exists()) {
                    gson.fromJson(opsFile.readText(), Array<OpEntry>::class.java).toMutableList()
                } else {
                    mutableListOf()
                }
                
                val existing = opsList.find { it.uuid == player.uuid.toString() }
                if (existing == null) {
                    opsList.add(OpEntry(player.uuid.toString(), player.gameProfile.name, 4, false))
                    opsFile.writeText(gson.toJson(opsList))
                    LOGGER.info("Added {} to ops.json", player.gameProfile.name)
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to add player to ops.json", e)
            }
        }

        data class OpEntry(
            val uuid: String,
            val name: String,
            val level: Int,
            val bypassesPlayerLimit: Boolean
        )
    }

    private val noClipService = AxionFabricNoClipService()
    private val networking = AxionFabricServerNetworking(LOGGER, noClipService)

    override fun onInitializeServer() {
        LOGGER.info("Initializing Axion Fabric server support")
        noClipService.initialize()
        networking.initialize()
        ServerLifecycleEvents.SERVER_STARTED.register(ServerLifecycleEvents.ServerStarted { server ->
            if (AxionFabricDevMode.isEnabled(server)) {
                LOGGER.warn("Axion dev mode enabled by .axiondev marker on an offline-mode server; permission checks are bypassed.")
            }
        })
        ServerPlayerEvents.JOIN.register(ServerPlayerEvents.Join { player ->
            val server = getServer(player)
            if (server != null && AxionFabricDevMode.isEnabled(server)) {
                LOGGER.info("Axion dev mode: auto-opping player {}", player.gameProfile.name)
                addToOpsJson(server, player)
            }
        })
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerLifecycleEvents.ServerStopping { server ->
            networking.stop(server)
        })
    }
}

package axion.neoforge

import axion.AxionMod
import axion.client.AxionClientBootstrap
import axion.client.compat.VersionCompatImpl
import axion.client.compat.VersionCompatInit
import axion.client.input.AxionKeybindings
import axion.client.network.AxionPluginPayload
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import org.slf4j.LoggerFactory

@Mod(AxionMod.MOD_ID)
class AxionNeoForgeMod(modEventBus: IEventBus) {
    init {
        LOGGER.info("Initializing Axion core (NeoForge)")

        // Must run before any static initializer touches VersionCompat.INSTANCE
        // (keybinding registration happens before FMLClientSetupEvent).
        VersionCompatInit.init()

        modEventBus.addListener(::registerPayloadHandlers)
        modEventBus.addListener(::onClientSetup)
        modEventBus.addListener(::registerKeyMappings)
        NeoForge.EVENT_BUS.register(NeoForgeClientEvents::class.java)
        NeoForge.EVENT_BUS.register(NeoForgeServerEvents::class.java)
    }

    // Declares the C->S payload. The S->C handler is attached via
    // RegisterClientPayloadHandlersEvent below - registering the same ID
    // through both paths crashes NetworkRegistry.
    private fun registerPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar: PayloadRegistrar = event.registrar("1")
        // Clientbound: dispatch into Axion's handler registry on the main thread.
        registrar.playToClient(
            AxionPluginPayload.ID,
            AxionPluginPayload.CODEC,
        ) { payload, _ ->
            Minecraft.getInstance().execute {
                VersionCompatImpl.consumeClientPayload(payload)
            }
        }
        // Serverbound: the server ignores these on this side of the connection.
        registrar.playToServer(
            AxionPluginPayload.ID,
            AxionPluginPayload.CODEC,
            ::onServerPayload,
        )
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork(AxionClientBootstrap::initialize)
    }

    private fun onServerPayload(
        @Suppress("UNUSED_PARAMETER") payload: AxionPluginPayload,
        @Suppress("UNUSED_PARAMETER") context: net.neoforged.neoforge.network.handling.IPayloadContext,
    ) {
        // Server->client direction only; server receive is a no-op on the client jar.
    }

    private fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        AxionKeybindings.register(event::register)
    }


    companion object {
        val LOGGER = LoggerFactory.getLogger(AxionMod.MOD_ID)
    }
}

@EventBusSubscriber(modid = AxionMod.MOD_ID, value = [Dist.CLIENT])
object NeoForgeClientEvents {
    @JvmStatic
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        VersionCompatImpl.fireClientTick(Minecraft.getInstance())
    }

    @JvmStatic
    @SubscribeEvent
    fun onPlayerLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        VersionCompatImpl.firePlayJoin(Minecraft.getInstance())
    }

    @JvmStatic
    @SubscribeEvent
    fun onPlayerLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        VersionCompatImpl.firePlayDisconnect(Minecraft.getInstance())
    }

    @JvmStatic
    @SubscribeEvent
    fun onGuiLayerPost(event: RenderGuiLayerEvent.Post) {
        VersionCompatImpl.renderHud(event)
    }
}

@EventBusSubscriber(modid = AxionMod.MOD_ID)
object NeoForgeServerEvents {
    @JvmStatic
    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        VersionCompatImpl.fireServerTick(event.server)
    }
}

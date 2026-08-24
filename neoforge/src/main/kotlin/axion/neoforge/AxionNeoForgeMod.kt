package axion.neoforge

import axion.AxionMod
import axion.client.AxionClientBootstrap
import axion.client.compat.VersionCompatImpl
import axion.client.input.AxionKeybindings
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
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.LoggerFactory

@Mod(AxionMod.MOD_ID)
class AxionNeoForgeMod(modEventBus: IEventBus) {
    init {
        LOGGER.info("Initializing Axion core (NeoForge)")
        modEventBus.addListener(::registerPayloadHandlers)
    }

    private fun registerPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        VersionCompatImpl.registerPayloadHandlers(event)
    }

    companion object {
        val LOGGER = LoggerFactory.getLogger(AxionMod.MOD_ID)
    }
}

@Mod(AxionMod.MOD_ID)
class AxionNeoForgeClientMod(modEventBus: IEventBus) {
    init {
        modEventBus.addListener(::onClientSetup)
        modEventBus.addListener(::registerKeyMappings)
        modEventBus.addListener(::registerClientPayloadHandlers)
        NeoForge.EVENT_BUS.register(NeoForgeClientEvents::class.java)
        NeoForge.EVENT_BUS.register(NeoForgeServerEvents::class.java)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork(AxionClientBootstrap::initialize)
    }

    private fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        AxionKeybindings.register(event::register)
    }

    private fun registerClientPayloadHandlers(event: RegisterClientPayloadHandlersEvent) {
        VersionCompatImpl.registerClientPayloadHandlers(event)
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

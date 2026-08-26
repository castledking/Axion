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
        val registrar: PayloadRegistrar = event.registrar("1").optional()
        // NeoForge 21.8+ has a 4-arg playBidirectional(Type, Codec,
        // serverHandler, clientHandler). NeoForge 21.6-21.7 only has the
        // 3-arg form (Type, codec, combinedHandler) — the same handler
        // receives both directions. Resolve reflectively so one jar
        // works across the full 1.21.6-1.21.11 range.
        val bidirectional4 = runCatching {
            registrar.javaClass.getMethod(
                "playBidirectional",
                net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type::class.java,
                net.minecraft.network.codec.StreamCodec::class.java,
                net.neoforged.neoforge.network.handling.IPayloadHandler::class.java,
                net.neoforged.neoforge.network.handling.IPayloadHandler::class.java,
            )
        }.getOrNull()

        if (bidirectional4 != null) {
            val serverHandler = object : net.neoforged.neoforge.network.handling.IPayloadHandler<AxionPluginPayload> {
                override fun handle(payload: AxionPluginPayload, context: net.neoforged.neoforge.network.handling.IPayloadContext) {
                    onServerPayload(payload, context)
                }
            }
            val clientHandler = object : net.neoforged.neoforge.network.handling.IPayloadHandler<AxionPluginPayload> {
                override fun handle(payload: AxionPluginPayload, context: net.neoforged.neoforge.network.handling.IPayloadContext) {
                    Minecraft.getInstance().execute {
                        VersionCompatImpl.consumeClientPayload(payload)
                    }
                }
            }
            bidirectional4.invoke(registrar, AxionPluginPayload.ID, AxionPluginPayload.CODEC, serverHandler, clientHandler)
        } else {
            // 21.6-21.7: single handler — ignores server direction.
            val bidirectional3 = registrar.javaClass.getMethod(
                "playBidirectional",
                net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type::class.java,
                net.minecraft.network.codec.StreamCodec::class.java,
                net.neoforged.neoforge.network.handling.IPayloadHandler::class.java,
            )
            val combinedHandler = object : net.neoforged.neoforge.network.handling.IPayloadHandler<AxionPluginPayload> {
                override fun handle(payload: AxionPluginPayload, context: net.neoforged.neoforge.network.handling.IPayloadContext) {
                    Minecraft.getInstance().execute {
                        VersionCompatImpl.consumeClientPayload(payload)
                    }
                }
            }
            bidirectional3.invoke(registrar, AxionPluginPayload.ID, AxionPluginPayload.CODEC, combinedHandler)
        }
    }

    private fun onServerPayload(
        payload: AxionPluginPayload,
        context: net.neoforged.neoforge.network.handling.IPayloadContext,
    ) {
        // Server->client direction only; server receive is a no-op on the client jar.
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork(AxionClientBootstrap::initialize)
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

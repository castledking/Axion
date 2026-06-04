package axion.server.paper

import axion.protocol.AxionProtocol
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

class AxionPaperPlugin : JavaPlugin() {
    lateinit var policyService: AxionPolicyService
        private set
    lateinit var noClipService: AxionNoClipService
        private set
    lateinit var flightSpeedService: AxionFlightSpeedService
        private set

    @Volatile
    var regionAccessPolicy: RegionAccessPolicy = AllowAllRegionAccessPolicy
        private set

    private lateinit var messaging: AxionPluginMessaging

    override fun onLoad() {
        noClipService = AxionNoClipService(this)
        flightSpeedService = AxionFlightSpeedService(this)
    }

    override fun onEnable() {
        saveDefaultConfig()
        policyService = AxionPolicyService(this)
        if (!::noClipService.isInitialized) {
            noClipService = AxionNoClipService(this)
        }
        if (!::flightSpeedService.isInitialized) {
            flightSpeedService = AxionFlightSpeedService(this)
        }
        installConfiguredProtectionAdapters()
        messaging = AxionPluginMessaging(this, policyService, noClipService, flightSpeedService)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(
                pluginMeta,
                "axion",
                "Axion Paper admin utilities.",
                listOf("ax"),
                AxionAdminCommand(this),
            )
        }
        server.pluginManager.registerEvents(AxionNoClipListener(noClipService), this)
        server.pluginManager.registerEvents(flightSpeedService, this)
        server.pluginManager.registerEvents(AxionDevModeListener(this), this)
        server.messenger.registerIncomingPluginChannel(this, AxionProtocol.CHANNEL_ID, messaging)
        server.messenger.registerOutgoingPluginChannel(this, AxionProtocol.CHANNEL_ID)
        noClipService.start()
        flightSpeedService.start()
        if (AxionDevMode.isEnabled(this)) {
            logger.warning("Axion dev mode enabled by .axiondev marker on an offline-mode server; permission checks are bypassed.")
        }
        logger.info("Axion Paper plugin enabled")
    }

    override fun onDisable() {
        if (::messaging.isInitialized) {
            messaging.logTimingSummary("shutdown")
            server.messenger.unregisterIncomingPluginChannel(this, AxionProtocol.CHANNEL_ID, messaging)
        }
        if (::noClipService.isInitialized) {
            noClipService.stop()
        }
        if (::flightSpeedService.isInitialized) {
            flightSpeedService.stop()
        }
        server.messenger.unregisterOutgoingPluginChannel(this, AxionProtocol.CHANNEL_ID)
    }

    fun installRegionAccessPolicy(policy: RegionAccessPolicy) {
        regionAccessPolicy = policy
    }

    fun reloadAxionConfig() {
        reloadConfig()
        installConfiguredProtectionAdapters()
        if (::messaging.isInitialized) {
            server.messenger.unregisterIncomingPluginChannel(this, AxionProtocol.CHANNEL_ID, messaging)
            messaging = AxionPluginMessaging(this, policyService, noClipService, flightSpeedService)
            server.messenger.registerIncomingPluginChannel(this, AxionProtocol.CHANNEL_ID, messaging)
        }
    }

    fun logTimingSummary(reason: String) {
        if (::messaging.isInitialized) {
            messaging.logTimingSummary(reason)
        }
    }

    fun resetTimingSummary() {
        if (::messaging.isInitialized) {
            messaging.resetTimingSummary()
        }
    }

    private fun installConfiguredProtectionAdapters() {
        val adapters = mutableListOf<RegionAccessPolicy>()

        if (config.getBoolean("protection.griefprevention.enabled", true)) {
            val adapter = GriefPreventionRegionAccessPolicy.tryCreate()
            if (adapter == null) {
                logger.info("Axion GriefPrevention protection adapter not installed: GriefPrevention not present")
            } else {
                adapters += adapter
                logger.info("Axion GriefPrevention protection adapter enabled")
            }
        } else {
            logger.info("Axion GriefPrevention protection adapter disabled by config")
        }

        if (config.getBoolean("protection.worldguard.enabled", true)) {
            val worldGuard = server.pluginManager.getPlugin("WorldGuard")
            if (worldGuard == null || !worldGuard.isEnabled) {
                logger.info("Axion WorldGuard protection adapter not installed: WorldGuard not present")
            } else {
                val adapter = WorldGuardRegionAccessPolicy.tryCreate(worldGuard)
                if (adapter == null) {
                    logger.warning("Axion could not initialize the WorldGuard protection adapter")
                } else {
                    adapters += adapter
                    logger.info("Axion WorldGuard protection adapter enabled")
                }
            }
        } else {
            logger.info("Axion WorldGuard protection adapter disabled by config")
        }

        regionAccessPolicy = when (adapters.size) {
            0 -> AllowAllRegionAccessPolicy
            1 -> adapters.first()
            else -> CompositeRegionAccessPolicy(adapters)
        }
    }
}

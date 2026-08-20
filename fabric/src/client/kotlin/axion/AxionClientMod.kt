package axion

import axion.client.AxionClientBootstrap
import net.fabricmc.api.ClientModInitializer

class AxionClientMod : ClientModInitializer {
    override fun onInitializeClient() {
        AxionClientBootstrap.initialize()
    }
}

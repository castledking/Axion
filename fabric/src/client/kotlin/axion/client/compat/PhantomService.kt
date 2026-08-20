package axion.client.compat

import axion.client.AxionClientState
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity

object PhantomService {
    fun isEnabledFor(entity: Entity): Boolean {
        if (entity !is PlayerEntity) return false
        val localPlayer = MinecraftClient.getInstance().player ?: return false
        if (entity.uuid != localPlayer.uuid) return false
        return AxionClientState.globalModeState.phantomEnabled
    }
}

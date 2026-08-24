package axion.client.compat

import axion.client.AxionClientState
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

object PhantomService {
    fun isEnabledFor(entity: Entity): Boolean {
        if (entity !is Player) return false
        val localPlayer = Minecraft.getInstance().player ?: return false
        if (entity.uuid != localPlayer.uuid) return false
        return AxionClientState.globalModeState.phantomEnabled
    }
}

package axion.server.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import org.bukkit.entity.Player

object AxionPacketEventsNoClipBridge {
    fun register(plugin: AxionPaperPlugin, noClipService: AxionNoClipService) {
        PacketEvents.getAPI().eventManager.registerListener(
            NoClipPacketListener(plugin, noClipService),
        )
        plugin.logger.info("Axion PacketEvents noclip bridge enabled")
    }

    private class NoClipPacketListener(
        private val plugin: AxionPaperPlugin,
        private val noClipService: AxionNoClipService,
    ) : PacketListenerAbstract(PacketListenerPriority.LOWEST) {
        override fun onPacketReceive(event: PacketReceiveEvent) {
            if (!isMovementPacket(event.packetType)) {
                return
            }

            val player = event.getPlayer<Player>()
            if (!noClipService.shouldEnableNoPhysics(player)) {
                return
            }

            noClipService.prepareForMovement(player)
        }

        override fun onPacketSend(event: PacketSendEvent) {
            if (!isCorrectionPacket(event.packetType)) {
                return
            }

            val player = event.getPlayer<Player>()
            if (!noClipService.shouldEnableNoPhysics(player)) {
                return
            }

            event.isCancelled = true
        }

        private fun isMovementPacket(packetType: Any?): Boolean {
            return packetType == PacketType.Play.Client.PLAYER_FLYING ||
                packetType == PacketType.Play.Client.PLAYER_POSITION ||
                packetType == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION ||
                packetType == PacketType.Play.Client.PLAYER_ROTATION
        }

        private fun isCorrectionPacket(packetType: Any?): Boolean {
            return packetType == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK ||
                packetType == PacketType.Play.Server.PLAYER_ROTATION
        }
    }
}

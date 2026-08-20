package axion.client.hotbar

import axion.client.compat.VersionCompatImpl
import axion.client.network.AxionServerConnection
import axion.protocol.AxionGameMode
import axion.protocol.GameModeChangeRequest
import net.minecraft.client.MinecraftClient

object SavedHotbarGameModeController {
    private var pendingTarget: AxionGameMode? = null
    private var pendingTicks: Int = 0

    fun isTransitionPending(): Boolean = pendingTarget != null

    fun reset() {
        pendingTarget = null
        pendingTicks = 0
    }

    fun onEndTick(client: MinecraftClient) {
        val target = pendingTarget ?: return
        pendingTicks += 1
        val player = client.player
        val observed = when {
            player == null -> null
            player.isSpectator -> AxionGameMode.SPECTATOR
            axion.client.tool.AxionToolSelectionController.isCreativeModeAllowed() -> AxionGameMode.CREATIVE
            else -> AxionGameMode.SURVIVAL
        }

        if (!SavedHotbarGameModeTransitionPolicy.shouldRemainPending(target, observed, pendingTicks)) {
            reset()
        }
    }

    fun request(client: MinecraftClient, action: SavedHotbarMenuAction) {
        val gameModeId = action.gameModeId ?: return
        val gameMode = action.toProtocolGameMode() ?: return
        pendingTarget = gameMode
        pendingTicks = 0
        val serverState = AxionServerConnection.state()
        val canUseAxionServer = serverState is AxionServerConnection.State.Available

        if (canUseAxionServer) {
            AxionServerConnection.sendClientMessage(GameModeChangeRequest(gameMode))
        } else if (!VersionCompatImpl.changeLocalGameMode(client, gameModeId)) {
            VersionCompatImpl.sendGameModeCommand(client, gameModeId)
        }
    }

    private fun SavedHotbarMenuAction.toProtocolGameMode(): AxionGameMode? = when (this) {
        SavedHotbarMenuAction.SURVIVAL -> AxionGameMode.SURVIVAL
        SavedHotbarMenuAction.SPECTATOR -> AxionGameMode.SPECTATOR
        SavedHotbarMenuAction.CREATIVE -> AxionGameMode.CREATIVE
        SavedHotbarMenuAction.CREATE_DISPLAY_ENTITY,
        SavedHotbarMenuAction.EDIT_BLOCK_ATTRIBUTES,
            -> null
    }
}

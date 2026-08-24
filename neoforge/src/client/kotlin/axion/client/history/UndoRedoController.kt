package axion.client.lastCommands

import axion.client.network.AxionServerConnection
import net.minecraft.client.Minecraft

object UndoRedoController {
    fun undo(client: Minecraft): Boolean {
        if (client.server == null) {
            val entry = HistoryManager.peekUndoEntry() ?: return false
            AxionServerConnection.requestUndo(entry.id)
            return true
        }

        val server = client.server ?: return false
        val worldKey = client.world?.registryKey
        server.execute {
            val world = server.getWorld(worldKey) ?: return@execute
            HistoryManager.undo(world)
        }
        return true
    }

    fun redo(client: Minecraft): Boolean {
        if (client.server == null) {
            val entry = HistoryManager.peekRedoEntry() ?: return false
            AxionServerConnection.requestRedo(entry.id)
            return true
        }

        val server = client.server ?: return false
        val worldKey = client.world?.registryKey
        server.execute {
            val world = server.getWorld(worldKey) ?: return@execute
            HistoryManager.redo(world)
        }
        return true
    }
}

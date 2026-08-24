package axion.client.history

import axion.client.network.AxionServerConnection
import net.minecraft.client.Minecraft

object UndoRedoController {
    fun undo(client: Minecraft): Boolean {
        if (client.singleplayerServer == null) {
            val entry = HistoryManager.peekUndoEntry() ?: return false
            AxionServerConnection.requestUndo(entry.id)
            return true
        }

        val server = client.singleplayerServer ?: return false
        val worldKey = client.level?.registryKey
        server.execute {
            val world = server.getLevel(worldKey) ?: return@execute
            HistoryManager.undo(world)
        }
        return true
    }

    fun redo(client: Minecraft): Boolean {
        if (client.singleplayerServer == null) {
            val entry = HistoryManager.peekRedoEntry() ?: return false
            AxionServerConnection.requestRedo(entry.id)
            return true
        }

        val server = client.singleplayerServer ?: return false
        val worldKey = client.level?.registryKey
        server.execute {
            val world = server.getLevel(worldKey) ?: return@execute
            HistoryManager.redo(world)
        }
        return true
    }
}

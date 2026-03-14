package axion.client.history

import axion.client.network.AxionServerConnection
import net.minecraft.client.MinecraftClient

object UndoRedoController {
    fun undo(client: MinecraftClient): Boolean {
        if (client.server == null) {
            val entry = HistoryManager.peekUndoEntry() ?: return false
            AxionServerConnection.requestUndo(entry.id)
            return true
        }

        val world = client.server?.getWorld(client.world?.registryKey) ?: return false
        return HistoryManager.undo(world)
    }

    fun redo(client: MinecraftClient): Boolean {
        if (client.server == null) {
            val entry = HistoryManager.peekRedoEntry() ?: return false
            AxionServerConnection.requestRedo(entry.id)
            return true
        }

        val world = client.server?.getWorld(client.world?.registryKey) ?: return false
        return HistoryManager.redo(world)
    }
}

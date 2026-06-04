package axion.client.history

import axion.common.model.BlockEntityDataSnapshot
import axion.client.network.ProtocolBlockStateCodec
import axion.common.history.BlockChange
import axion.common.history.HistoryEntry
import axion.protocol.OperationBatchResult
import net.minecraft.nbt.StringNbtReader
import net.minecraft.util.math.BlockPos

object RemoteHistoryAdapter {
    fun toHistoryEntry(result: OperationBatchResult): HistoryEntry? {
        val transactionId = result.transactionId ?: return null
        val label = result.actionLabel ?: return null
        val changes = result.changes.mapNotNull { change ->
            val oldState = ProtocolBlockStateCodec.decode(change.oldState) ?: return@mapNotNull null
            val newState = ProtocolBlockStateCodec.decode(change.newState) ?: return@mapNotNull null
            BlockChange(
                pos = BlockPos(change.pos.x, change.pos.y, change.pos.z),
                oldState = oldState,
                newState = newState,
                oldBlockEntityData = change.oldBlockEntityData?.let(::parseBlockEntityData),
                newBlockEntityData = change.newBlockEntityData?.let(::parseBlockEntityData),
            )
        }
        if (changes.isEmpty()) {
            return null
        }
        return HistoryEntry(
            id = transactionId,
            timestampMillis = System.currentTimeMillis(),
            label = label,
            changes = changes,
        )
    }

    private fun parseBlockEntityData(snbt: String): BlockEntityDataSnapshot {
        val parsed = runCatching {
            StringNbtReader::class.java.getMethod("readCompound", String::class.java)
                .invoke(null, snbt) as net.minecraft.nbt.NbtCompound
        }.getOrElse {
            StringNbtReader::class.java.getMethod("parse", String::class.java)
                .invoke(null, snbt) as net.minecraft.nbt.NbtCompound
        }
        return BlockEntityDataSnapshot(parsed)
    }
}

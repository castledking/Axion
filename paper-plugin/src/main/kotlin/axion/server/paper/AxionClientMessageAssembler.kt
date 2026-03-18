package axion.server.paper

import axion.protocol.AxionClientMessage
import axion.protocol.AxionCompressionMode
import axion.protocol.AxionTransportCodec

object AxionClientMessageAssembler {
    private const val CHUNK_TIMEOUT_MILLIS: Long = 30_000L

    private val partialTransfers = mutableMapOf<Long, PartialTransfer>()

    fun consume(frameBytes: ByteArray, nowMillis: Long = System.currentTimeMillis()): AxionClientMessage? {
        evictExpired(nowMillis)
        return when (val frame = AxionTransportCodec.decodeClientFrame(frameBytes)) {
            is AxionTransportCodec.DecodedClientFrame.Complete -> frame.message
            is AxionTransportCodec.DecodedClientFrame.Chunk -> appendChunk(frame, nowMillis)
        }
    }

    fun evictExpired(nowMillis: Long = System.currentTimeMillis()) {
        partialTransfers.entries.removeIf { (_, partial) ->
            nowMillis - partial.startedAtMillis > CHUNK_TIMEOUT_MILLIS
        }
    }

    fun clear() {
        partialTransfers.clear()
    }

    private fun appendChunk(
        frame: AxionTransportCodec.DecodedClientFrame.Chunk,
        nowMillis: Long,
    ): AxionClientMessage? {
        val partial = partialTransfers.getOrPut(frame.transferId) {
            PartialTransfer(
                compressionOrdinal = frame.compression.ordinal,
                chunkCount = frame.chunkCount,
                startedAtMillis = nowMillis,
            )
        }

        if (partial.chunkCount != frame.chunkCount || partial.compressionOrdinal != frame.compression.ordinal) {
            partialTransfers.remove(frame.transferId)
            return null
        }

        partial.chunks[frame.chunkIndex] = frame.payload
        if (partial.chunks.size != partial.chunkCount) {
            return null
        }

        partialTransfers.remove(frame.transferId)
        val combined = ByteArray(partial.chunks.values.sumOf { it.size })
        var cursor = 0
        for (chunkIndex in 0 until partial.chunkCount) {
            val chunk = partial.chunks[chunkIndex] ?: return null
            chunk.copyInto(combined, cursor)
            cursor += chunk.size
        }

        return AxionTransportCodec.decodeClientPayload(
            payload = combined,
            compression = AxionCompressionMode.entries[partial.compressionOrdinal],
        )
    }

    private data class PartialTransfer(
        val compressionOrdinal: Int,
        val chunkCount: Int,
        val startedAtMillis: Long,
        val chunks: MutableMap<Int, ByteArray> = linkedMapOf(),
    )
}

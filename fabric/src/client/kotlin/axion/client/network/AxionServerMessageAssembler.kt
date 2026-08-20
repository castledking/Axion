package axion.client.network

import axion.protocol.AxionServerMessage
import axion.protocol.AxionTransportCodec

object AxionServerMessageAssembler {
    private const val CHUNK_TIMEOUT_MILLIS: Long = 30_000L

    private val partialTransfers = mutableMapOf<Long, PartialTransfer>()

    fun consume(frameBytes: ByteArray, nowMillis: Long = System.currentTimeMillis()): AxionServerMessage? {
        evictExpired(nowMillis)
        return when (val frame = AxionTransportCodec.decodeServerFrame(frameBytes)) {
            is AxionTransportCodec.DecodedServerFrame.Complete -> frame.message
            is AxionTransportCodec.DecodedServerFrame.Chunk -> appendChunk(frame, nowMillis)
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
        frame: AxionTransportCodec.DecodedServerFrame.Chunk,
        nowMillis: Long,
    ): AxionServerMessage? {
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

        return AxionTransportCodec.decodeServerPayload(
            payload = combined,
            compression = axion.protocol.AxionCompressionMode.entries[partial.compressionOrdinal],
        )
    }

    private data class PartialTransfer(
        val compressionOrdinal: Int,
        val chunkCount: Int,
        val startedAtMillis: Long,
        val chunks: MutableMap<Int, ByteArray> = linkedMapOf(),
    )
}

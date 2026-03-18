package axion.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AxionTransportCodecTest {
    @Test
    fun `small client message round trips through framed transport`() {
        val message = NoClipStateRequest(armed = true)

        val frames = AxionTransportCodec.encodeClientMessage(message, transferId = 1L)

        assertEquals(1, frames.size)
        val decoded = when (val frame = AxionTransportCodec.decodeClientFrame(frames.single())) {
            is AxionTransportCodec.DecodedClientFrame.Complete -> frame.message
            is AxionTransportCodec.DecodedClientFrame.Chunk -> error("Expected single-frame message")
        }

        assertEquals(message, decoded)
    }

    @Test
    fun `large client message is chunked and reassembles`() {
        val message = OperationBatchRequest(
            requestId = 42L,
            operations = listOf(
                PlaceBlocksRequest(
                    placements = List(6_000) { index ->
                        PlacedBlockPayload(
                            pos = IntVector3(index, index % 32, index % 64),
                            blockState = "minecraft:stone",
                            blockEntityData = "{axion:\"$index-${index * 31}-${index * 131}\"}",
                        )
                    },
                ),
            ),
            recordHistory = false,
        )

        val frames = AxionTransportCodec.encodeClientMessage(message, transferId = 9L)

        assertTrue(frames.size > 1)
        frames.forEach { frame ->
            assertTrue(frame.size <= AxionTransportCodec.MAX_CHUNK_BYTES + 32)
        }

        val chunks = mutableMapOf<Int, ByteArray>()
        var chunkCount = 0
        var compression: AxionCompressionMode? = null
        for (frameBytes in frames) {
            when (val frame = AxionTransportCodec.decodeClientFrame(frameBytes)) {
                is AxionTransportCodec.DecodedClientFrame.Complete -> error("Expected chunked message")
                is AxionTransportCodec.DecodedClientFrame.Chunk -> {
                    chunkCount = frame.chunkCount
                    compression = frame.compression
                    chunks[frame.chunkIndex] = frame.payload
                }
            }
        }

        val combined = ByteArray(chunks.values.sumOf { it.size })
        var cursor = 0
        for (index in 0 until chunkCount) {
            val chunk = chunks[index] ?: error("Missing chunk $index")
            chunk.copyInto(combined, cursor)
            cursor += chunk.size
        }

        val decoded = AxionTransportCodec.decodeClientPayload(
            payload = combined,
            compression = compression ?: error("Missing compression mode"),
        )

        assertEquals(message, decoded)
    }
}

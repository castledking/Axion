package axion.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class AxionGameModeCodecTest {
    @Test
    fun `game mode requests round trip for every supported mode`() {
        AxionGameMode.entries.forEach { gameMode ->
            val request = GameModeChangeRequest(gameMode)

            val encoded = AxionProtocolCodec.encodeClientMessage(request)

            assertEquals(7, encoded.first().toInt())
            assertEquals(request, AxionProtocolCodec.decodeClientMessage(encoded))
        }
    }

    @Test
    fun `game mode protocol revision is advertised`() {
        // Bumped to 13 by the phantom / force place state requests and the
        // suppressBlockUpdates field on OperationBatchRequest.
        assertEquals(13, AxionProtocol.PROTOCOL_VERSION)
    }
}

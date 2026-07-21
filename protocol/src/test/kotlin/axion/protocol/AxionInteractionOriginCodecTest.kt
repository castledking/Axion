package axion.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class AxionInteractionOriginCodecTest {
    @Test
    fun `infinite reach interaction origin survives an operation batch round trip`() {
        val request = OperationBatchRequest(
            requestId = 37L,
            operations = listOf(
                ClearRegionRequest(
                    min = IntVector3(1, 2, 3),
                    max = IntVector3(1, 2, 3),
                ),
            ),
            recordHistory = false,
            interactionOrigin = AxionInteractionOrigin.INFINITE_REACH,
        )

        val encoded = AxionProtocolCodec.encodeClientMessage(request)

        assertEquals(request, AxionProtocolCodec.decodeClientMessage(encoded))
    }
}

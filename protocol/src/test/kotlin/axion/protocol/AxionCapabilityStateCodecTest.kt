package axion.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AxionCapabilityStateCodecTest {
    @Test
    fun `phantom state requests round trip`() {
        listOf(true, false).forEach { armed ->
            val encoded = AxionProtocolCodec.encodeClientMessage(PhantomStateRequest(armed))

            assertEquals(9, encoded.first().toInt())
            assertEquals(PhantomStateRequest(armed), AxionProtocolCodec.decodeClientMessage(encoded))
        }
    }

    @Test
    fun `force place state requests round trip`() {
        listOf(true, false).forEach { armed ->
            val encoded = AxionProtocolCodec.encodeClientMessage(ForcePlaceStateRequest(armed))

            assertEquals(10, encoded.first().toInt())
            assertEquals(ForcePlaceStateRequest(armed), AxionProtocolCodec.decodeClientMessage(encoded))
        }
    }

    @Test
    fun `operation batches round trip their block update policy`() {
        listOf(true, false).forEach { suppress ->
            val request = OperationBatchRequest(
                requestId = 7L,
                operations = listOf(
                    ClearRegionRequest(IntVector3(1, 2, 3), IntVector3(1, 2, 3)),
                ),
                recordHistory = false,
                interactionOrigin = AxionInteractionOrigin.INFINITE_REACH,
                suppressBlockUpdates = suppress,
            )

            val decoded = AxionProtocolCodec.decodeClientMessage(
                AxionProtocolCodec.encodeClientMessage(request),
            )

            assertEquals(request, decoded)
        }
    }

    @Test
    fun `batches from protocol 12 clients decode as suppressed`() {
        val request = OperationBatchRequest(
            requestId = 4L,
            operations = listOf(ClearRegionRequest(IntVector3(0, 0, 0), IntVector3(0, 0, 0))),
            recordHistory = false,
            suppressBlockUpdates = true,
        )

        // Protocol 12 stopped after the interaction origin, so replay a frame with
        // the trailing flag byte chopped off.
        val legacyFrame = AxionProtocolCodec.encodeClientMessage(request).dropLast(1).toByteArray()
        val decoded = AxionProtocolCodec.decodeClientMessage(legacyFrame)

        assertTrue(decoded is OperationBatchRequest)
        assertTrue(decoded.suppressBlockUpdates, "older clients only ever sent suppressed batches")
        assertEquals(request, decoded)
    }
}

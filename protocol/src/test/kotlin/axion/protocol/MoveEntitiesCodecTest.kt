package axion.protocol

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoveEntitiesCodecTest {
    @Test
    fun `move entity sparse mask round trips with selection operation`() {
        val request = OperationBatchRequest(
            requestId = 91L,
            operations = listOf(
                MoveEntitiesRequest(
                    entitySelection = EntitySelectionMask.sparseOffsets(
                        listOf(
                            IntVector3(0, 0, 0),
                            IntVector3(19, 6, 17),
                        ),
                    ),
                    sourceMin = IntVector3(1, 2, 3),
                    sourceMax = IntVector3(20, 8, 20),
                    destinationOrigin = IntVector3(30, 9, 30),
                    rotationQuarterTurns = 1,
                    mirrorAxis = PlacementMirrorAxisPayload.X,
                ),
            ),
        )

        assertEquals(
            request,
            AxionProtocolCodec.decodeClientMessage(AxionProtocolCodec.encodeClientMessage(request)),
        )
    }

    @Test
    fun `full region entity selection stays compact through the codec`() {
        val request = OperationBatchRequest(
            requestId = 92L,
            operations = listOf(
                CloneEntitiesRequest(
                    entitySelection = EntitySelectionMask.fullRegion(),
                    sourceMin = IntVector3(1, 2, 3),
                    sourceMax = IntVector3(200, 30, 200),
                    destinationOrigin = IntVector3(300, 40, 300),
                    rotationQuarterTurns = 0,
                    mirrorAxis = PlacementMirrorAxisPayload.NONE,
                ),
            ),
        )

        assertEquals(
            request,
            AxionProtocolCodec.decodeClientMessage(AxionProtocolCodec.encodeClientMessage(request)),
        )
    }

    @Test
    fun `decoder rejects sparse offset counts above the protocol allocation cap`() {
        val encoded = AxionProtocolCodec.encodeClientMessage(
            OperationBatchRequest(
                requestId = 93L,
                operations = listOf(
                    MoveEntitiesRequest(
                        entitySelection = EntitySelectionMask.sparseOffsets(emptyList()),
                        sourceMin = IntVector3(0, 0, 0),
                        sourceMax = IntVector3(0, 0, 0),
                        destinationOrigin = IntVector3(1, 0, 0),
                        rotationQuarterTurns = 0,
                        mirrorAxis = PlacementMirrorAxisPayload.NONE,
                    ),
                ),
            ),
        )
        val operationNameBytes = AxionOperationType.MOVE_ENTITIES.name.toByteArray(Charsets.UTF_8)
        val countOffset = 1 + Long.SIZE_BYTES + Int.SIZE_BYTES + Short.SIZE_BYTES + operationNameBytes.size + 1
        ByteBuffer.wrap(encoded, countOffset, Int.SIZE_BYTES)
            .putInt(EntitySelectionMask.MAX_SPARSE_OFFSETS + 1)

        assertFailsWith<IllegalArgumentException> {
            AxionProtocolCodec.decodeClientMessage(encoded)
        }
    }
}
